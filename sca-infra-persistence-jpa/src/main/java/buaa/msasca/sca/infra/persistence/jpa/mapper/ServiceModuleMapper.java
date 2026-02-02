package buaa.msasca.sca.infra.persistence.jpa.mapper;

import buaa.msasca.sca.core.domain.model.ServiceModule;
import buaa.msasca.sca.infra.persistence.jpa.entity.project.ServiceModuleEntity;

public class ServiceModuleMapper {
    public ServiceModule toDomain(ServiceModuleEntity e) {
        return new ServiceModule(
            e.getId(),
            e.getProjectVersion().getId(),
            e.getName(),
            e.getRootPath(),
            e.getBuildTool(),
            e.getJdkVersion(),
            e.isGateway()
        );
    }
}