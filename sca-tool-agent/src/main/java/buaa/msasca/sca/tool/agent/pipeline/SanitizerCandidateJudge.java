package buaa.msasca.sca.tool.agent.pipeline;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Sanitizer 후보 판정기 (Judge).
 *
 * <p><b>역할:</b> Miner가 수집한 각 후보가 "실제 sanitizer인지" LLM으로 판정한다.</p>
 *
 * <p><b>동작:</b></p>
 * <ul>
 *   <li>후보를 {@value #BATCH_SIZE}개 단위로 묶어 LLM 배치 호출 (후보 수 ÷ 배치 크기 ≈ LLM 호출 수)</li>
 *   <li>출력: actionType(escape/validate/guard/normalize/reject), vulnTypes, confidence(0~1), reasoning</li>
 *   <li>CodeQL 연관 후보(codeql, codeql-flow 신호)는 프롬프트에 ruleId/message/flow 맥락 추가, confidence 가산</li>
 *   <li>API 키 없음 또는 호출 실패 시 휴리스틱(메서드명 기반)으로 폴백</li>
 * </ul>
 *
 * <p><b>주의:</b> 본 단계 결과는 "판정"일 뿐, 최종 확정은 Verifier에서 규칙으로 재검증한다.</p>
 */
public class SanitizerCandidateJudge {

    private static final int BATCH_SIZE = 50;
    private static final ObjectMapper OM = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();
    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public SanitizerCandidateJudge(String apiKey, String baseUrl, String model) {
        this.apiKey = apiKey;
        this.baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "https://openrouter.ai/api/v1" : baseUrl;
        this.model = (model == null || model.isBlank()) ? "deepseek/deepseek-r1" : model;
    }

    public List<JudgedSanitizerCandidate> judge(List<SanitizerCandidate> candidates) {
        if (candidates.isEmpty()) return List.of();

        List<JudgedSanitizerCandidate> out = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, candidates.size());
            List<SanitizerCandidate> batch = candidates.subList(i, end);
            out.addAll(judgeBatch(batch));
        }
        return out;
    }

    private List<JudgedSanitizerCandidate> judgeBatch(List<SanitizerCandidate> batch) {
        if (apiKey == null || apiKey.isBlank()) {
            return batch.stream().map(c -> heuristic(c, "no-api-key")).toList();
        }
        try {
            String prompt = buildBatchPrompt(batch);
            String content = callLlm(prompt);
            JsonNode arr = parseJsonArrayFromContent(content);
            return parseBatchResponse(arr, batch);
        } catch (Exception e) {
            return batch.stream()
                .map(c -> heuristic(c, "llm-fallback:" + e.getClass().getSimpleName()))
                .toList();
        }
    }

    private List<JudgedSanitizerCandidate> parseBatchResponse(JsonNode arr, List<SanitizerCandidate> batch) {
        List<JudgedSanitizerCandidate> out = new ArrayList<>();
        int len = arr != null && arr.isArray() ? arr.size() : 0;
        for (int i = 0; i < batch.size(); i++) {
            SanitizerCandidate c = batch.get(i);
            JsonNode parsed = (i < len) ? arr.get(i) : null;
            if (parsed == null || !parsed.isObject()) {
                out.add(heuristic(c, "llm-missing-item"));
                continue;
            }
            String action = textOrDefault(parsed.path("actionType"), classifyAction(c.methodName()));
            double confidence = asDoubleOr(parsed.path("confidence"), score(c));
            List<String> vulns = parseVulnTypes(parsed.path("vulnTypes"));
            if (vulns.isEmpty()) {
                vulns = List.of("SQLi", "XSS", "Command Injection", "Path Traversal");
            }
            String reasoning = textOrDefault(parsed.path("reasoning"),
                "llm signals=" + c.signals() + ", file=" + c.filePath());
            out.add(new JudgedSanitizerCandidate(c, action, vulns, confidence, reasoning));
        }
        return out;
    }

    private String buildBatchPrompt(List<SanitizerCandidate> batch) {
        StringBuilder sb = new StringBuilder();
        // 역할 지정: 보안 분석기
        sb.append("You are security analyzer.\n");
        // 판정 과제: 각 후보 메서드가 입력 테인트 제거용 sanitizer인지 여부 판정
        sb.append("Judge whether each candidate method behaves as sanitizer for input taint reduction.\n");
        // 출력 형식: JSON 배열만 반환, 각 요소에 actionType·vulnTypes·confidence·reasoning 포함
        sb.append("Return ONLY a JSON array. Each element: {actionType, vulnTypes, confidence, reasoning}.\n");
        // actionType 허용값
        sb.append("actionType one of: escape, validate, guard, normalize, reject.\n");
        // confidence 범위 (0~1)
        sb.append("confidence between 0 and 1.\n");
        // 후보 목록 시작 (인덱스로 응답 배열 순서와 매핑)
        sb.append("Candidates (indexed 0..n-1):\n");
        for (int i = 0; i < batch.size(); i++) {
            SanitizerCandidate c = batch.get(i);
            sb.append("[")
                .append(i)
                .append("] methodName: ")
                .append(c.methodName())
                .append(", filePath: ")
                .append(c.filePath())
                .append(", signals: ")
                .append(c.signals())
                .append("\n");
            // CodeQL SARIF 연관 후보는 ruleId·message·flow 맥락 추가 (판정 품질 향상)
            if (c.codeqlFindingId() != null) {
                sb.append("  [CodeQL] ruleId: ")
                    .append(c.codeqlRuleId() != null ? c.codeqlRuleId() : "")
                    .append(", message: ")
                    .append(c.codeqlMessage() != null ? c.codeqlMessage() : "")
                    .append("\n");
                if (c.flowStepLabels() != null && !c.flowStepLabels().isEmpty()) {
                    sb.append("  [CodeQL] flow context: ")
                        .append(String.join(", ", c.flowStepLabels()))
                        .append("\n");
                }
            }
        }
        return sb.toString();
    }

    private JsonNode parseJsonArrayFromContent(String text) throws Exception {
        String t = (text == null) ? "" : text.trim();
        t = t.replace("```json", "").replace("```", "").trim();
        int i = t.indexOf('[');
        int j = t.lastIndexOf(']');
        if (i >= 0 && j > i) {
            t = t.substring(i, j + 1);
        } else {
            i = t.indexOf('{');
            j = t.lastIndexOf('}');
            if (i >= 0 && j > i) {
                t = "[" + t.substring(i, j + 1) + "]";
            }
        }
        return OM.readTree(t);
    }

    private JudgedSanitizerCandidate heuristic(SanitizerCandidate c, String tag) {
        double confidence = score(c);
        String action = classifyAction(c.methodName());
        List<String> vulns = List.of("SQLi", "XSS", "Command Injection", "Path Traversal");
        String reasoning = "heuristic(" + tag + "), signals=" + c.signals() + ", action=" + action + ", confidence=" + confidence;
        return new JudgedSanitizerCandidate(c, action, vulns, confidence, reasoning);
    }

    private String callLlm(String prompt) throws Exception {
        ObjectNode req = OM.createObjectNode();
        req.put("model", model);
        ArrayNode msgs = req.putArray("messages");
        msgs.addObject().put("role", "user").put("content", prompt);
        req.put("temperature", 0.1);

        HttpRequest httpReq = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl.endsWith("/") ? (baseUrl + "chat/completions") : (baseUrl + "/chat/completions")))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(OM.writeValueAsString(req)))
            .build();

        HttpResponse<String> res = client.send(httpReq, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IllegalStateException("llm status=" + res.statusCode());
        }

        JsonNode root = OM.readTree(res.body());
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.isNull()) {
            throw new IllegalStateException("llm empty content");
        }
        return content.asText("");
    }

    private List<String> parseVulnTypes(JsonNode n) {
        if (n == null || !n.isArray()) return List.of();
        List<String> out = new ArrayList<>();
        for (JsonNode x : n) {
            String s = x.asText("").trim();
            if (!s.isBlank()) out.add(s);
        }
        return out;
    }

    private String textOrDefault(JsonNode n, String def) {
        String s = (n == null || n.isMissingNode() || n.isNull()) ? null : n.asText(null);
        return (s == null || s.isBlank()) ? def : s;
    }

    private double asDoubleOr(JsonNode n, double def) {
        if (n == null || n.isMissingNode() || n.isNull()) return def;
        if (n.isNumber()) return n.asDouble();
        try {
            return Double.parseDouble(n.asText(String.valueOf(def)));
        } catch (Exception e) {
            return def;
        }
    }

    private double score(SanitizerCandidate c) {
        double base = 0.35;
        if (c.signals().contains("name")) base += 0.35;
        if (c.signals().contains("annotation")) base += 0.2;
        // CodeQL SARIF에서 추출된 후보는 신뢰도 가산 (codeql-flow가 있으면 추가 가산)
        if (c.signals().contains("codeql")) base += 0.15;
        if (c.signals().contains("codeql-flow")) base += 0.1;
        String n = c.methodName().toLowerCase();
        if (n.contains("sanitize") || n.contains("escape") || n.contains("validate")) {
            base += 0.1;
        }
        return Math.min(base, 0.95);
    }

    private String classifyAction(String methodName) {
        String n = methodName.toLowerCase(Locale.ROOT);
        if (n.contains("escape")) return "escape";
        if (n.contains("normalize")) return "normalize";
        if (n.contains("guard") || n.contains("check")) return "guard";
        if (n.contains("reject")) return "reject";
        return "validate";
    }
}

