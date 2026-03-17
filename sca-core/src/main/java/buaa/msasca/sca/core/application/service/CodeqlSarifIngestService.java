package buaa.msasca.sca.core.application.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import buaa.msasca.sca.core.domain.enums.CodeqlSummaryStatus;
import buaa.msasca.sca.core.port.out.persistence.CodeqlResultPort;
import buaa.msasca.sca.core.application.error.ToolExecutionException;

public class CodeqlSarifIngestService {

    private final CodeqlResultPort resultPort;
    private final ObjectMapper om;

    public CodeqlSarifIngestService(CodeqlResultPort resultPort) {
        this.resultPort = resultPort;
        this.om = new ObjectMapper();
    }

    public void ingest(
        Long codeqlToolRunId,
        Long serviceModuleId,
        String sarifPathOnHost,
        String sarifStoragePath
    ) {
        Instant now = Instant.now();

        try {
        byte[] bytes = Files.readAllBytes(Path.of(sarifPathOnHost));
        String sha256 = sha256Hex(bytes);

        JsonNode root = om.readTree(bytes);
        JsonNode run0 = root.path("runs").path(0);

        // 1) rules 메타 맵 생성
        Map<String, RuleMeta> ruleMetaMap = buildRuleMetaMap(run0);

        // 2) results
        JsonNode results = run0.path("results");
        int count = results.isArray() ? results.size() : 0;

        if (count == 0) {
            resultPort.upsertRunSummary(
                codeqlToolRunId,
                serviceModuleId,
                CodeqlSummaryStatus.CLEAN,
                0,
                sarifStoragePath,
                sha256,
                now
            );
            return;
        }

        List<CodeqlResultPort.CodeqlFindingIngest> findings = new ArrayList<>(count);

        for (JsonNode r : results) {
            String ruleId = textOrEmpty(r.path("ruleId"));
            RuleMeta meta = ruleMetaMap.get(ruleId);

            // message
            String message = textOrEmpty(r.path("message").path("text"));

            // level: result.level 우선, 없으면 rule 기본 레벨
            String level = r.path("level").isMissingNode() || r.path("level").isNull()
                ? (meta != null ? meta.level : null)
                : r.path("level").asText(null);

            // tags/helpText: rule 메타에서
            JsonNode tagsJson = (meta != null ? meta.tagsJson : null);
            String helpText = (meta != null ? meta.helpText : null);

            // primary location
            String primaryFile = null;
            Integer primaryLine = null;
            JsonNode pl0 = r.path("locations").path(0).path("physicalLocation");
            if (!pl0.isMissingNode()) {
            primaryFile = pl0.path("artifactLocation").path("uri").asText(null);
            JsonNode startLine = pl0.path("region").path("startLine");
            if (startLine.isInt()) primaryLine = startLine.asInt();
            }

            // locations
            List<CodeqlResultPort.LocationRow> locRows = parseLocations(r);

            // flows (codeFlows.threadFlows.locations)
            List<CodeqlResultPort.FlowRow> flows = parseFlows(r);

            findings.add(new CodeqlResultPort.CodeqlFindingIngest(
                ruleId,
                message,
                level,
                tagsJson,
                helpText,
                primaryFile,
                primaryLine,
                locRows,
                flows
            ));
        }

        // summary upsert
        resultPort.upsertRunSummary(
            codeqlToolRunId,
            serviceModuleId,
            CodeqlSummaryStatus.HAS_RESULTS,
            findings.size(),
            sarifStoragePath,
            sha256,
            now
        );

        // replace all
        resultPort.replaceAll(codeqlToolRunId, findings);

        } catch (Exception e) {
        resultPort.upsertRunSummary(
            codeqlToolRunId,
            serviceModuleId,
            CodeqlSummaryStatus.INGEST_FAILED,
            0,
            sarifStoragePath,
            null,
            now
        );
        throw ToolExecutionException.codeql(
            codeqlToolRunId,
            "SARIF ingest failed: toolRunId=" + codeqlToolRunId + " path=" + sarifPathOnHost,
            e
        );
        }
    }

    // ---------------------------
    // rules -> meta map
    // ---------------------------
    private Map<String, RuleMeta> buildRuleMetaMap(JsonNode run0) {
        Map<String, RuleMeta> map = new HashMap<>();

        JsonNode rules = run0.path("tool").path("driver").path("rules");
        if (!rules.isArray()) return map;

        for (JsonNode rule : rules) {
        String id = rule.path("id").asText(null);
        if (id == null || id.isBlank()) continue;

        // level: defaultConfiguration.level (있으면)
        String level = null;
        JsonNode dc = rule.path("defaultConfiguration");
        if (!dc.isMissingNode()) {
            level = dc.path("level").asText(null);
        }

        // tags: rule.properties.tags 가 흔함 (배열)
        JsonNode tagsJson = null;
        JsonNode props = rule.path("properties");
        JsonNode tags = props.path("tags");
        if (tags.isArray()) {
            // 그대로 jsonb에 저장 가능한 ArrayNode로 사용
            tagsJson = tags;
        }

        // helpText: help.text 우선, 없으면 fullDescription.text / shortDescription.text
        String helpText = textOrNull(rule.path("help").path("text"));
        if (helpText == null) helpText = textOrNull(rule.path("fullDescription").path("text"));
        if (helpText == null) helpText = textOrNull(rule.path("shortDescription").path("text"));

        map.put(id, new RuleMeta(level, tagsJson, helpText));
        }

        return map;
    }

    private static final class RuleMeta {
        final String level;
        final JsonNode tagsJson;
        final String helpText;
        RuleMeta(String level, JsonNode tagsJson, String helpText) {
        this.level = level;
        this.tagsJson = tagsJson;
        this.helpText = helpText;
        }
    }

    // ---------------------------
    // locations
    // ---------------------------
    private List<CodeqlResultPort.LocationRow> parseLocations(JsonNode result) {
        List<CodeqlResultPort.LocationRow> locRows = new ArrayList<>();
        JsonNode locs = result.path("locations");
        if (!locs.isArray()) return locRows;

        int idx = 0;
        for (JsonNode loc : locs) {
        JsonNode pl = loc.path("physicalLocation");
        String file = pl.path("artifactLocation").path("uri").asText(null);
        JsonNode line = pl.path("region").path("startLine");
        if (file != null && !file.isBlank() && line.isInt()) {
            locRows.add(new CodeqlResultPort.LocationRow(idx, file, line.asInt()));
            idx++;
        }
        }
        return locRows;
    }

    // ---------------------------
    // flows: codeFlows[*].threadFlows[*].locations[*].location
    // ---------------------------
    private List<CodeqlResultPort.FlowRow> parseFlows(JsonNode result) {
        List<CodeqlResultPort.FlowRow> flows = new ArrayList<>();
        JsonNode codeFlows = result.path("codeFlows");
        if (!codeFlows.isArray()) return flows;

        int flowIndex = 0;
        for (JsonNode cf : codeFlows) {
        List<CodeqlResultPort.FlowStepRow> steps = new ArrayList<>();

        JsonNode threadFlows = cf.path("threadFlows");
        if (threadFlows.isArray()) {
            // threadFlow 여러 개면 전부 이어붙이거나, 0번만 쓰거나 정책이 필요함
            // 여기서는 전부 이어붙임(순서 유지)
            int stepIndex = 0;
            for (JsonNode tf : threadFlows) {
            JsonNode locs = tf.path("locations");
            if (!locs.isArray()) continue;

            for (JsonNode step : locs) {
                JsonNode loc = step.path("location");
                JsonNode pl = loc.path("physicalLocation");

                String file = pl.path("artifactLocation").path("uri").asText(null);

                Integer line = null;
                JsonNode lineNode = pl.path("region").path("startLine");
                if (lineNode.isInt()) line = lineNode.asInt();

                String label = textOrNull(loc.path("message").path("text"));
                if (label == null) label = textOrNull(step.path("state").path("message").path("text")); // 케이스 커버

                if (file != null || line != null || label != null) {
                steps.add(new CodeqlResultPort.FlowStepRow(stepIndex, file, line, label));
                stepIndex++;
                }
            }
            }
        }

        flows.add(new CodeqlResultPort.FlowRow(flowIndex, steps));
        flowIndex++;
        }

        return flows;
    }

    // ---------------------------
    // helpers
    // ---------------------------
    private static String textOrEmpty(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return "";
        return n.asText("");
    }

    private static String textOrNull(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return null;
        String s = n.asText(null);
        return (s == null || s.isBlank()) ? null : s;
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] dig = md.digest(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : dig) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}