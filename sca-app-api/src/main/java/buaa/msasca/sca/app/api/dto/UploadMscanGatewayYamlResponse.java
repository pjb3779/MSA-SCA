package buaa.msasca.sca.app.api.dto;

public record UploadMscanGatewayYamlResponse(
    MscanGatewayYamlResponse gatewayYaml,
    Long analysisRunId,
    String autoRunError
) {}