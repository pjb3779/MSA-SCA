package buaa.msasca.sca.infra.persistence.jpa.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import buaa.msasca.sca.infra.persistence.jpa.adapter.JpaAnalysisRunAdapter;
import buaa.msasca.sca.infra.persistence.jpa.adapter.JpaArtifactAdapter;
import buaa.msasca.sca.infra.persistence.jpa.adapter.JpaProjectAdapter;
import buaa.msasca.sca.infra.persistence.jpa.adapter.JpaProjectVersionAdapter;
import buaa.msasca.sca.infra.persistence.jpa.adapter.JpaProjectVersionSourceCacheAdapter;
import buaa.msasca.sca.infra.persistence.jpa.adapter.JpaServiceModuleCommandAdapter;
import buaa.msasca.sca.infra.persistence.jpa.adapter.JpaServiceModuleAdapter;
import buaa.msasca.sca.infra.persistence.jpa.adapter.JpaToolRunAdapter;

import buaa.msasca.sca.infra.persistence.jpa.mapper.AnalysisRunMapper;
import buaa.msasca.sca.infra.persistence.jpa.mapper.ArtifactMapper;
import buaa.msasca.sca.infra.persistence.jpa.mapper.ProjectMapper;
import buaa.msasca.sca.infra.persistence.jpa.mapper.ProjectVersionViewMapper;
import buaa.msasca.sca.infra.persistence.jpa.mapper.ServiceModuleMapper;
import buaa.msasca.sca.infra.persistence.jpa.mapper.SourceCacheMapper;
import buaa.msasca.sca.infra.persistence.jpa.mapper.ToolRunMapper;

import buaa.msasca.sca.infra.persistence.jpa.repository.AgentRunDetailJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.AnalysisArtifactJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.AnalysisRunJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.BuildRunDetailJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.CodeqlRunDetailJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.MscanRunDetailJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.ProjectJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.ProjectVersionJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.ProjectVersionSourceCacheJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.ServiceModuleJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.ToolRunJpaRepository;

/**
 * Persistence Wiring
 * - JPA Repository + Mapper + Adapter 조립
 * - core Port 인터페이스는 Adapter가 직접 구현한다고 가정하고,
 *   "Port로 다시 감싼 빈"을 만들지 않는다(중복 빈 방지).
 */
@Configuration
public class PersistenceWiringConfig {

    // =========================================================
    // MAPPERS
    // =========================================================
    @Bean
    public ProjectMapper projectMapper() {
        return new ProjectMapper();
    }

    @Bean
    public ProjectVersionViewMapper projectVersionViewMapper() {
        return new ProjectVersionViewMapper();
    }

    @Bean
    public SourceCacheMapper sourceCacheMapper() {
        return new SourceCacheMapper();
    }

    @Bean
    public AnalysisRunMapper analysisRunMapper() {
        return new AnalysisRunMapper();
    }

    @Bean
    public ServiceModuleMapper serviceModuleMapper() {
        return new ServiceModuleMapper();
    }

    @Bean
    public ToolRunMapper toolRunMapper() {
        return new ToolRunMapper();
    }

    @Bean
    public ArtifactMapper artifactMapper() {
        return new ArtifactMapper();
    }

    // =========================================================
    // ADAPTER BEANS (한 구현체 = 한 빈)
    // ※ Adapter가 Port/CommandPort를 직접 구현하면 이 빈 하나로 주입된다.
    // =========================================================

    // ---- Project ----
    @Bean
    public JpaProjectAdapter projectAdapter(
        ProjectJpaRepository repo,
        ProjectMapper mapper
    ) {
        return new JpaProjectAdapter(repo, mapper);
    }

    // ---- ProjectVersion ----
    @Bean
    public JpaProjectVersionAdapter projectVersionAdapter(
        ProjectVersionJpaRepository repo,
        ProjectJpaRepository projectRepo,
        ProjectVersionViewMapper mapper
    ) {
        return new JpaProjectVersionAdapter(repo, projectRepo, mapper);
    }

    // ---- ProjectVersionSourceCache ----
    @Bean
    public JpaProjectVersionSourceCacheAdapter sourceCacheAdapter(
        ProjectVersionSourceCacheJpaRepository repo,
        ProjectVersionJpaRepository pvRepo,
        SourceCacheMapper mapper
    ) {
        return new JpaProjectVersionSourceCacheAdapter(repo, pvRepo, mapper);
    }

    // ---- AnalysisRun ----
    @Bean
    public JpaAnalysisRunAdapter analysisRunAdapter(
        AnalysisRunJpaRepository runRepo,
        ProjectVersionJpaRepository pvRepo,
        AnalysisRunMapper mapper
    ) {
        return new JpaAnalysisRunAdapter(runRepo, pvRepo, mapper);
    }

    // ---- ServiceModule (READ) ----
    @Bean
    public JpaServiceModuleAdapter serviceModuleAdapter(
        ServiceModuleJpaRepository repo,
        ServiceModuleMapper mapper
    ) {
        return new JpaServiceModuleAdapter(repo, mapper);
    }

    // ---- ServiceModule (COMMAND) ----
    @Bean
    public JpaServiceModuleCommandAdapter serviceModuleCommandAdapter(
        ServiceModuleJpaRepository repo,
        ProjectVersionJpaRepository pvRepo,
        ServiceModuleMapper mapper
    ) {
        return new JpaServiceModuleCommandAdapter(repo, pvRepo, mapper);
    }

    // ---- ToolRun ----
    @Bean
    public JpaToolRunAdapter toolRunAdapter(
        ToolRunJpaRepository toolRunRepo,
        AnalysisRunJpaRepository analysisRunRepo,
        ServiceModuleJpaRepository serviceModuleRepo,
        BuildRunDetailJpaRepository buildDetailRepo,
        CodeqlRunDetailJpaRepository codeqlDetailRepo,
        AgentRunDetailJpaRepository agentDetailRepo,
        MscanRunDetailJpaRepository mscanDetailRepo,
        ToolRunMapper mapper
    ) {
        return new JpaToolRunAdapter(
            toolRunRepo,
            analysisRunRepo,
            serviceModuleRepo,
            buildDetailRepo,
            codeqlDetailRepo,
            agentDetailRepo,
            mscanDetailRepo,
            mapper
        );
    }

    // ---- AnalysisArtifact ----
    @Bean
    public JpaArtifactAdapter analysisArtifactAdapter(
        AnalysisArtifactJpaRepository repo,
        ToolRunJpaRepository toolRunRepo,
        ArtifactMapper mapper
    ) {
        return new JpaArtifactAdapter(repo, toolRunRepo, mapper);
    }
}