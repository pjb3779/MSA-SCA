package buaa.msasca.sca.infra.persistence.jpa.adapter;

import java.util.Optional;

import buaa.msasca.sca.core.domain.model.ProjectVersionView;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionPort;
import buaa.msasca.sca.infra.persistence.jpa.mapper.ProjectVersionViewMapper;
import buaa.msasca.sca.infra.persistence.jpa.repository.ProjectVersionJpaRepository;

public class JpaProjectVersionAdapter implements ProjectVersionPort {
    private final ProjectVersionJpaRepository repo;
    private final ProjectVersionViewMapper mapper;

    public JpaProjectVersionAdapter(ProjectVersionJpaRepository repo, ProjectVersionViewMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    //project_version을 조회
    @Override
    public Optional<ProjectVersionView> findById(Long projectVersionId) {
        return repo.findById(projectVersionId).map(mapper::toView);
    }
}
