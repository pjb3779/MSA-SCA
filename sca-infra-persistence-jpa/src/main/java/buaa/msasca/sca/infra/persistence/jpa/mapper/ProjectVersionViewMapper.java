package buaa.msasca.sca.infra.persistence.jpa.mapper;

import buaa.msasca.sca.core.domain.model.ProjectVersionView;
import buaa.msasca.sca.infra.persistence.jpa.entity.project.ProjectVersionEntity;

public class ProjectVersionViewMapper {
    
    //조회용으로 변환
    public ProjectVersionView toView(ProjectVersionEntity e) {
        return new ProjectVersionView(
            e.getId(),
            e.getProject().getId(),
            e.getVersionLabel(),
            e.getSourceType(),
            e.getSourceUrl(),
            e.getUploadFilePath(),
            e.getVcsCommitHash()
        );
    }
}