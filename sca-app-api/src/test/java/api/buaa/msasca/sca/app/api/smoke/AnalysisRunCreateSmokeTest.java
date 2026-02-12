package api.buaa.msasca.sca.app.api.smoke;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import buaa.msasca.sca.app.api.SCAApiApplication;
import buaa.msasca.sca.core.domain.enums.RunStatus;
import buaa.msasca.sca.core.domain.enums.SourceType;
import buaa.msasca.sca.core.domain.model.AnalysisRun;
import buaa.msasca.sca.core.port.out.persistence.AnalysisRunCommandPort;
import buaa.msasca.sca.infra.persistence.jpa.entity.project.ProjectEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.project.ProjectVersionEntity;
import buaa.msasca.sca.infra.persistence.jpa.entity.project.ProjectVersionSourceCacheEntity;
import buaa.msasca.sca.infra.persistence.jpa.repository.ProjectJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.ProjectVersionJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.ProjectVersionSourceCacheJpaRepository;

/**
 * - AnalysisRunPort.create() 호출 시 analysis_run row가 DB에 생성되는지 검증한다.
 */
@SpringBootTest(classes = SCAApiApplication.class)
@Import(buaa.msasca.sca.infra.persistence.jpa.config.PersistenceWiringConfig.class)
class AnalysisRunCreateSmokeTest {

    @Autowired
    AnalysisRunCommandPort analysisRunPort;

    @Autowired
    ProjectJpaRepository projectRepo;

    @Autowired
    ProjectVersionJpaRepository projectVersionRepo;

    @Autowired
    ProjectVersionSourceCacheJpaRepository sourceCacheRepo;

    private final ObjectMapper om = new ObjectMapper();

    @Test
    @Transactional
    void analysis_run_is_created() throws Exception {

        // 1) project
        ProjectEntity project = ProjectEntity.create(
            "analysis-run-smoke",
            "analysis run smoke test",
            null
        );
        project = projectRepo.save(project);

        // 2) project_version (실제 시그니처 6개 인자)
        ProjectVersionEntity pv = ProjectVersionEntity.create(
            project,
            "v-smoke-1",
            SourceType.ZIP,
            null,   // source_url
            null,   // upload_file_path
            null    // vcs_commit_hash
        );
        pv = projectVersionRepo.save(pv);

        // 3) source cache (실제 시그니처 3개 인자)
        Path tempDir = Files.createTempDirectory("msasca-src-");

        ProjectVersionSourceCacheEntity cache =
            ProjectVersionSourceCacheEntity.create(
                pv,
                tempDir.toAbsolutePath().toString(),
                (Instant) null
            );
        sourceCacheRepo.save(cache);

        // 4) analysis_run 생성
        ObjectNode cfg = om.createObjectNode();
        cfg.put("mode", "smoke-test");

        AnalysisRun run =
            analysisRunPort.createPending(pv.getId(), cfg, "api-smoke-test");

        // 5) 검증
        assertThat(run).isNotNull();
        assertThat(run.id()).isNotNull();
        assertThat(run.projectVersionId()).isEqualTo(pv.getId());
        assertThat(run.status()).isEqualTo(RunStatus.PENDING);
    }
}