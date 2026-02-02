package buaa.msasca.sca.infra.persistence.jpa.mapper;

import buaa.msasca.sca.core.domain.model.Project;
import buaa.msasca.sca.infra.persistence.jpa.entity.project.ProjectEntity;

public class ProjectMapper {

    public Project toDomain(ProjectEntity e) {
        return new Project(
            e.getId(),
            e.getName(),
            e.getDescription(),
            e.getRepoUrl(),
            e.getCreatedAt(),
            e.getUpdatedAt()
        );
    }
}