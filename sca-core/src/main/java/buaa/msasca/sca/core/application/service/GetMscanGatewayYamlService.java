package buaa.msasca.sca.core.application.service;

import java.util.Optional;

import buaa.msasca.sca.core.domain.model.MscanGatewayYaml;
import buaa.msasca.sca.core.port.in.GetMscanGatewayYamlUseCase;
import buaa.msasca.sca.core.port.out.persistence.MscanGatewayYamlPort;

public class GetMscanGatewayYamlService implements GetMscanGatewayYamlUseCase {

    private final MscanGatewayYamlPort port;

    public GetMscanGatewayYamlService(MscanGatewayYamlPort port) {
        this.port = port;
    }

    @Override
    public Optional<MscanGatewayYaml> get(Long projectVersionId) {
        return port.findByProjectVersionId(projectVersionId);
    }
}