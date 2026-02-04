package buaa.msasca.sca.infra.persistence.jpa.adapter;

import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import buaa.msasca.sca.core.domain.enums.SourceType;
import buaa.msasca.sca.core.domain.model.ProjectVersionView;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionCommandPort;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionPort;
import buaa.msasca.sca.infra.persistence.jpa.entity.project.ProjectEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.project.ProjectVersionEntity;
import buaa.msasca.sca.infra.persistence.jpa.mapper.ProjectVersionViewMapper;
import buaa.msasca.sca.infra.persistence.jpa.repository.ProjectJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.ProjectVersionJpaRepository;

public class JpaProjectVersionAdapter implements ProjectVersionPort, ProjectVersionCommandPort {
    
    private final ProjectVersionJpaRepository repo;
    private final ProjectJpaRepository projectRepo;
    private final ProjectVersionViewMapper mapper;

    public JpaProjectVersionAdapter(ProjectVersionJpaRepository repo, ProjectJpaRepository projectRepo, ProjectVersionViewMapper mapper) {
        this.repo = repo;
        this.projectRepo = projectRepo;
        this.mapper = mapper;
    }

    /**
     * 프로젝트 버전을 생성한다.
     */
    @Override
    public ProjectVersionView create(Long projectId, String versionLabel, SourceType sourceType, String sourceUrl,
                                String uploadFilePath, String vcsCommitHash) {
        ProjectEntity project = projectRepo.findById(projectId)
            .orElseThrow(() -> new IllegalArgumentException("project not found: " + projectId));

        ProjectVersionEntity saved = repo.save(ProjectVersionEntity.create(
            project, versionLabel, sourceType, sourceUrl, uploadFilePath, vcsCommitHash
        ));

        return mapper.toDomain(saved);
    }

    /**
     * 프로젝트 버전을 조회한다.
     */
    @Override
    public Optional<ProjectVersionView> findById(Long projectVersionId) {
        return repo.findById(projectVersionId).map(mapper::toDomain);
    }

    /**
   * upload_file_path를 갱신한다.
   *
   * @param projectVersionId project_version PK
   * @param uploadFilePath 로컬 zip 파일 경로
   */
    @Override
    @Transactional
    public void updateUploadFilePath(Long projectVersionId, String uploadFilePath) {
        ProjectVersionEntity pv = repo.findById(projectVersionId)
            .orElseThrow(() -> new IllegalArgumentException("project_version not found: " + projectVersionId));

        pv.changeUploadFilePath(uploadFilePath);
    }
}