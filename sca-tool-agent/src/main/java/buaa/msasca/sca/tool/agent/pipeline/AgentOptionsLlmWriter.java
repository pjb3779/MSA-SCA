package buaa.msasca.sca.tool.agent.pipeline;

import java.net.URI;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Agent 옵션 YAML 생성기.
 *
 * <p>verified sanitizers와 gateway entries를 바탕으로 MScan sidecar용 YAML을 생성한다.</p>
 * <p>스키마 계약(schemaVersion=v1)은 코드에서 고정 보장하며, LLM 출력 형식에 의존하지 않는다.</p>
 */
public class AgentOptionsLlmWriter {

    private static final Logger log = LoggerFactory.getLogger(AgentOptionsLlmWriter.class);
    private static final ObjectMapper OM = new ObjectMapper();
    private static final String AGENT_OPTIONS_TASK = """
        You are generating a YAML configuration file for a security scan agent.

        Given the following JSON input, produce a valid YAML file with this structure:

        # Agent-generated sidecar options
        schemaVersion: v1
        agent:
          sanitizerRegistry: <sanitizerRegistryPath>
          gatewayEntries: <gatewayEntriesPath>

        Requirements:
        - Use exactly the paths provided in the input.
        - You may add brief comments about confirmed sanitizers or gateway entries if helpful.
        - Output ONLY valid YAML, no markdown code blocks or extra text.
        - Root keys must be exactly: "schemaVersion" and "agent".
        - "schemaVersion" must be exactly "v1".
        - "agent" must contain "sanitizerRegistry" and "gatewayEntries" as direct children.

        Input JSON:
        %s
        """;

    private final java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public AgentOptionsLlmWriter(String apiKey, String baseUrl, String model) {
        this.apiKey = apiKey;
        this.baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "https://openrouter.ai/api/v1" : baseUrl;
        this.model = (model == null || model.isBlank()) ? "deepseek/deepseek-r1" : model;
    }

    public String write(
        List<VerifiedSanitizerCandidate> verified,
        GatewayEntryLlmExtractor.GatewayEntries gatewayEntries,
        String sanitizerRegistryPath,
        String gatewayEntriesPath
    ) throws Exception {
        ObjectNode input = OM.createObjectNode();
        input.put("sanitizerRegistryPath", sanitizerRegistryPath);
        input.put("gatewayEntriesPath", gatewayEntriesPath);
        ArrayNode sanitizers = input.putArray("confirmedSanitizers");
        for (VerifiedSanitizerCandidate v : verified) {
            if (v.status() != SanitizerStatus.CONFIRMED) continue;
            ObjectNode s = sanitizers.addObject();
            s.put("method", v.candidate().methodName());
            s.put("action", v.actionType());
            ArrayNode vulns = s.putArray("vulnTypes");
            for (String vt : v.vulnTypes()) vulns.add(vt);
        }
        ArrayNode ext = input.putArray("externalEntries");
        for (String e : gatewayEntries.externalEntries()) ext.add(e);
        ArrayNode in = input.putArray("internalEntries");
        for (String i : gatewayEntries.internalEntries()) in.add(i);

        // 옵션 파일은 검증 스크립트가 엄격히 확인하므로 계약 스키마를 코드에서 고정 보장한다.
        // (LLM 호출은 부가 검토 목적의 텔레메트리 성격으로 유지하되, 실패해도 생성은 계속 진행)
        String prompt = AGENT_OPTIONS_TASK.formatted(OM.writerWithDefaultPrettyPrinter().writeValueAsString(input));
        tryCallLlm(prompt);
        return buildContractYaml(sanitizerRegistryPath, gatewayEntriesPath);
    }

    private void tryCallLlm(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[AgentOptions] skipping LLM telemetry: API key is empty");
            return;
        }
        try {
            callLlm(prompt);
        } catch (Exception e) {
            String reason = (e.getMessage() == null || e.getMessage().isBlank()) ? e.toString() : e.getMessage();
            log.warn("[AgentOptions] LLM telemetry failed but continue with contract YAML: {}", reason);
        }
    }

    private String callLlm(String prompt) throws Exception {
        ObjectNode req = OM.createObjectNode();
        req.put("model", model);
        ArrayNode msgs = req.putArray("messages");
        msgs.addObject().put("role", "user").put("content", prompt);
        req.put("temperature", 0.1);

        java.net.http.HttpRequest httpReq = java.net.http.HttpRequest.newBuilder()
            .uri(URI.create(baseUrl.endsWith("/") ? (baseUrl + "chat/completions") : (baseUrl + "/chat/completions")))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(OM.writeValueAsString(req)))
            .build();

        java.net.http.HttpResponse<String> res = client.send(httpReq, java.net.http.HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IllegalStateException("LLM status=" + res.statusCode());
        }

        JsonNode root = OM.readTree(res.body());
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.isNull()) {
            throw new IllegalStateException("LLM empty content");
        }
        return content.asText("");
    }

    private String extractYaml(String text) {
        String t = (text == null) ? "" : text.trim();
        t = t.replace("```yaml", "").replace("```yml", "").replace("```", "").trim();
        return t;
    }

    private String buildContractYaml(String sanitizerRegistryPath, String gatewayEntriesPath) {
        String sanitizerRegistry = singleQuotedYaml(sanitizerRegistryPath);
        String gatewayEntries = singleQuotedYaml(gatewayEntriesPath);
        return String.join(
            "\n",
            "# Agent-generated sidecar options",
            "schemaVersion: v1",
            "agent:",
            "  sanitizerRegistry: " + sanitizerRegistry,
            "  gatewayEntries: " + gatewayEntries,
            ""
        );
    }

    private String singleQuotedYaml(String raw) {
        String safe = (raw == null) ? "" : raw;
        return "'" + safe.replace("'", "''") + "'";
    }
}
