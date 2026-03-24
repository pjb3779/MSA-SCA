package buaa.msasca.sca.tool.agent.pipeline;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * gateway entry LLM 추출기.
 * MScan gateway_entry_scan과 동일한 프롬프트/few-shot 사용.
 * 폴백 없음. apiKey 없거나 호출 실패 시 예외.
 */
public class GatewayEntryLlmExtractor {

    private static final ObjectMapper OM = new ObjectMapper();
    private static final String GATEWAY_ENTRY_SCAN_TASK = """
        You are given a gateway configuration YAML file.

        Please identify all entry points (routes) and classify them as either public/external or internal, according to the following guidelines:

        Entry points should be considered public/external if, for example:
        - There are no IP address restrictions; the path is accessible from the public network.
        - There are no filters restricting requests by HTTP status (e.g., not blocked by 403/401).
        - The route has explicit configuration tags such as `expose`, `external`, or `public`.
        - There are no authentication or role-based access restrictions.
        - The route is marked as a public-facing API.

        Entry points should be considered internal if, for example:
        - The route is subject to IP whitelisting or limited to private/internal networks.
        - Access is restricted to admins, internal services, or specific roles.
        - Internal headers, tokens, or specific referers are required that would not be available to public users.
        - The route is protected by 403/401 or custom filters blocking ordinary users.
        - The route is labeled as `internal`, `private`, `admin`, or similar.
        - It is described as "for admin/internal use only," a service-only API, or a management endpoint.

        Your task:
        - List all public/external entry points.
        - Then, list all internal entry points.
        - No explanation or reasoning is necessary; just the lists.

        Tips:
        - An entry with explicit forwarding rules is definitely labeled as external, even if it appears to be an admin service.
        - You only need to focus on URL-level forwarding rules, without concerning yourself with host-level forwarding rules.
        - The output format must be a standard JSON string, not a code block starting with ```..
        - If there is a specific blacklist path, you must output it into the internal entries.

        The gateway config YAML is below:
        """;

    private final java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public GatewayEntryLlmExtractor(String apiKey, String baseUrl, String model) {
        this.apiKey = apiKey;
        this.baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "https://openrouter.ai/api/v1" : baseUrl;
        this.model = (model == null || model.isBlank()) ? "deepseek/deepseek-r1" : model;
    }

    public record GatewayEntries(List<String> externalEntries, List<String> internalEntries) {}

    public GatewayEntries extract(String gatewayYamlPathOnHost) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("LLM API key is required for gateway entry extraction");
        }
        if (gatewayYamlPathOnHost == null || gatewayYamlPathOnHost.isBlank()) {
            throw new IllegalArgumentException("gateway YAML path is required");
        }
        Path p = Path.of(gatewayYamlPathOnHost);
        if (!Files.exists(p) || !Files.isRegularFile(p)) {
            throw new IllegalStateException("gateway YAML not found: " + gatewayYamlPathOnHost);
        }
        String yamlContent = Files.readString(p, StandardCharsets.UTF_8);
        String prompt = buildPrompt(yamlContent);
        String content = callLlm(prompt);
        return parseResponse(content);
    }

    private String buildPrompt(String yamlContent) {
        StringBuilder sb = new StringBuilder();
        sb.append(GATEWAY_ENTRY_SCAN_TASK);
        sb.append("\n\n--- Examples ---\n\n");
        for (GatewayEntryExamples.Example ex : GatewayEntryExamples.all()) {
            sb.append("Input:\n").append(ex.input()).append("\nOutput:\n").append(ex.output()).append("\n\n");
        }
        sb.append("--- Now process the following. Return ONLY a JSON object with external_entries and internal_entries ---\n\n");
        sb.append(yamlContent);
        return sb.toString();
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

    private GatewayEntries parseResponse(String text) throws Exception {
        String t = (text == null) ? "" : text.trim();
        t = t.replace("```json", "").replace("```", "").trim();
        int i = t.indexOf('{');
        int j = t.lastIndexOf('}');
        if (i >= 0 && j > i) {
            t = t.substring(i, j + 1);
        }
        JsonNode node = OM.readTree(t);
        List<String> external = parseStringArray(node.path("external_entries"));
        List<String> internal = parseStringArray(node.path("internal_entries"));
        return new GatewayEntries(external, internal);
    }

    private List<String> parseStringArray(JsonNode n) {
        if (n == null || !n.isArray()) return List.of();
        List<String> out = new ArrayList<>();
        for (JsonNode x : n) {
            String s = x.asText("").trim();
            if (!s.isBlank()) out.add(s);
        }
        return out;
    }
}
