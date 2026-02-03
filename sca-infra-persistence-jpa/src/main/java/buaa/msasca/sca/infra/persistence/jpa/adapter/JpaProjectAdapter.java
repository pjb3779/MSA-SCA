package buaa.msasca.sca.infra.persistence.jpa.adapter;

import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import buaa.msasca.sca.core.domain.model.Project;
import buaa.msasca.sca.core.port.out.persistence.ProjectPort;
import buaa.msasca.sca.infra.persistence.jpa.entity.project.ProjectEntity;
import buaa.msasca.sca.infra.persistence.jpa.mapper.ProjectMapper;
import buaa.msasca.sca.infra.persistence.jpa.repository.ProjectJpaRepository;

public class JpaProjectAdapter implements ProjectPort {

    private final ProjectJpaRepository repo;
    private final ProjectMapper mapper;

    public JpaProjectAdapter(ProjectJpaRepository repo, ProjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public Project create(String name, String description, String repoUrl) {
        ProjectEntity e = ProjectEntity.create(name, description, repoUrl);
        ProjectEntity saved = repo.save(e);
        return mapper.toDomain(saved);
    }
  
    @Override
    @Transactional
    public Project save(Project project) {
        if (project.id() == null) {
        ProjectEntity created = ProjectEntity.create(project.name(), project.description(), project.repoUrl());
        return mapper.toDomain(repo.save(created));
        }

        ProjectEntity e = repo.findById(project.id())
            .orElseThrow(() -> new IllegalArgumentException("project not found: " + project.id()));

        // setter 안 쓰고, 엔티티 메서드로 변경 (ProjectEntity에 아래 메서드들이 있어야 함)
        e.changeName(project.name());
        e.changeDescription(project.description());
        e.changeRepoUrl(project.repoUrl());

        return mapper.toDomain(e);
    }

    @Override
    public Optional<Project> findById(Long projectId) {
        return repo.findById(projectId).map(mapper::toDomain);
    }
}