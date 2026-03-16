package buaa.msasca.sca.core.port.in;

import java.io.InputStream;
import java.time.Instant;

import buaa.msasca.sca.core.domain.enums.GatewayYamlProvidedBy;
import buaa.msasca.sca.core.domain.model.MscanGatewayYaml;

public interface UploadMscanGatewayYamlUseCase {
    record Request(
        Long projectVersionId,
        String originalFilename,
        InputStream content,
        GatewayYamlProvidedBy providedBy
    ) {}

    MscanGatewayYaml upload(Request req);
}