package buaa.msasca.sca.core.port.in;

import buaa.msasca.sca.core.domain.model.MscanGatewayYaml;

public interface EnsureMscanGatewayYamlUseCase {
    /**
     * 없으면 status=MISSING 레코드를 만든 뒤 반환한다.
     * 이미 있으면(READY/MISSING) 그대로 반환한다.
     */
    MscanGatewayYaml ensure(Long projectVersionId);
}