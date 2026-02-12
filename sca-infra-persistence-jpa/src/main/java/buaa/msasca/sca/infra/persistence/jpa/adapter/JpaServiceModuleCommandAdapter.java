package buaa.msasca.sca.infra.persistence.jpa.adapter;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;

import buaa.msasca.sca.core.domain.enums.BuildTool;
import buaa.msasca.sca.core.domain.model.ServiceModule;
import buaa.msasca.sca.core.port.out.persistence.ServiceModuleCommandPort;
import buaa.msasca.sca.core.port.out.persistence.ServiceModulePort;
import buaa.msasca.sca.infra.persistence.jpa.entity.project.ProjectVersionEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.project.ServiceModuleEntity;
import buaa.msasca.sca.infra.persistence.jpa.mapper.ServiceModuleMapper;
import buaa.msasca.sca.infra.persistence.jpa.repository.ProjectVersionJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.ServiceModuleJpaRepository;

public class JpaServiceModuleCommandAdapter implements  ServiceModuleCommandPort {

    private final ServiceModuleJpaRepository repo;
    private final ProjectVersionJpaRepository pvRepo;
    private final ServiceModuleMapper mapper;

    public JpaServiceModuleCommandAdapter(
        ServiceModuleJpaRepository repo,
        ProjectVersionJpaRepository pvRepo,
        ServiceModuleMapper mapper
    ) {
        this.repo = repo;
        this.pvRepo = pvRepo;
        this.mapper = mapper;
    }

    /**
     * (projectVersionId, name) 기준으로 upsert한다.
     *
     * @param projectVersionId project_version ID
     * @param name name
     * @param rootPath root_path
     * @param buildTool build tool
     * @param jdkVersion jdk version
     * @param isGateway gateway
     * @return 저장된 ServiceModule
     */
    @Override
    @Transactional
    public ServiceModule upsert(
        Long projectVersionId,
        String name,
        String rootPath,
        BuildTool buildTool,
        String jdkVersion,
        boolean isGateway
    ) {
        ProjectVersionEntity pv = pvRepo.findById(projectVersionId)
            .orElseThrow(() -> new IllegalArgumentException("project_version not found: " + projectVersionId));

        ServiceModuleEntity e = repo.findByProjectVersion_IdAndName(projectVersionId, name)
            .orElseGet(() -> ServiceModuleEntity.create(
                pv, name, rootPath, buildTool, jdkVersion, isGateway
            ));

        // 기존이면 변경 반영
        e.changeRootPath(rootPath);
        e.changeBuildTool(buildTool);
        e.changeJdkVersion(jdkVersion);
        e.changeGateway(isGateway);

        ServiceModuleEntity saved = repo.save(e);
        return mapper.toDomain(saved);
    }
}