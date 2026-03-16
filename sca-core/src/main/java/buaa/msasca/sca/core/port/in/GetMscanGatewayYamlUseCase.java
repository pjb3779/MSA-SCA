package buaa.msasca.sca.core.port.in;

import java.util.Optional;

import buaa.msasca.sca.core.domain.model.MscanGatewayYaml;

public interface GetMscanGatewayYamlUseCase {
  Optional<MscanGatewayYaml> get(Long projectVersionId);
}