package buaa.msasca.sca.core.application.service;

import buaa.msasca.sca.core.domain.model.MscanGatewayYaml;
import buaa.msasca.sca.core.port.in.EnsureMscanGatewayYamlUseCase;
import buaa.msasca.sca.core.port.out.persistence.MscanGatewayYamlCommandPort;

public class EnsureMscanGatewayYamlService implements EnsureMscanGatewayYamlUseCase {

    // 프로젝트에서 고정 경로로 쓰기로 한 값
    public static final String DEFAULT_CACHE_REL_PATH = ".msasca/mscan/gateway.yml";

    private final MscanGatewayYamlCommandPort commandPort;

    public EnsureMscanGatewayYamlService(MscanGatewayYamlCommandPort commandPort) {
        this.commandPort = commandPort;
    }

    @Override
    public MscanGatewayYaml ensure(Long projectVersionId) {
        return commandPort.ensureMissing(projectVersionId, DEFAULT_CACHE_REL_PATH);
    }
}