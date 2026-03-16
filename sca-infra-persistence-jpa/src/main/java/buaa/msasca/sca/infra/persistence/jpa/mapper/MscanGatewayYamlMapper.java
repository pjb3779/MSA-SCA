package buaa.msasca.sca.infra.persistence.jpa.mapper;

import buaa.msasca.sca.core.domain.model.MscanGatewayYaml;
import buaa.msasca.sca.infra.persistence.jpa.entity.result.mscan.MscanGatewayYamlEntity;

public class MscanGatewayYamlMapper {
    public MscanGatewayYaml toDomain(MscanGatewayYamlEntity e) {
        return new MscanGatewayYaml(
            e.getId(),
            e.getProjectVersion().getId(),
            e.getStatus(),
            e.getProvidedBy(),
            e.getStoragePath(),
            e.getSha256(),
            e.getOriginalFilename(),
            e.getCacheRelPath(),
            e.getMetadataJson(),
            e.getCreatedAt(),
            e.getUpdatedAt()
        );
    }
}