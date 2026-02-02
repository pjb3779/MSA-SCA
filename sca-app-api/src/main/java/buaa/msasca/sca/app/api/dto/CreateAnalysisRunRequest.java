package buaa.msasca.sca.app.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record CreateAnalysisRunRequest(
    JsonNode configJson,
    String triggeredBy,
    Boolean requireSourceCache
) {}
