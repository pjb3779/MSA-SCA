package buaa.msasca.sca.app.api.dto;

import buaa.msasca.sca.core.domain.enums.GatewayYamlProvidedBy;
import buaa.msasca.sca.core.domain.enums.GatewayYamlStatus;
import buaa.msasca.sca.core.domain.model.MscanGatewayYaml;

public record MscanGatewayYamlResponse(
    Long projectVersionId,
    GatewayYamlStatus status,
    GatewayYamlProvidedBy providedBy,
    String storagePath,
    String sha256,
    String originalFilename,
    String cacheRelPath
) {
    public static MscanGatewayYamlResponse from(MscanGatewayYaml y) {
        return new MscanGatewayYamlResponse(
            y.projectVersionId(),
            y.status(),
            y.providedBy(),
            y.storagePath(),
            y.sha256(),
            y.originalFilename(),
            y.cacheRelPath()
        );
    }
}