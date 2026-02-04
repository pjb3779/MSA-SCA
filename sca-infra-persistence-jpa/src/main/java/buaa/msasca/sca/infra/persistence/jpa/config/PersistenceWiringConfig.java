package buaa.msasca.sca.infra.persistence.jpa.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import buaa.msasca.sca.core.port.out.persistence.AnalysisRunPort;
import buaa.msasca.sca.core.port.out.persistence.ProjectPort;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionSourceCacheCommandPort;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionSourceCachePort;

import buaa.msasca.sca.infra.persistence.jpa.adapter.JpaAnalysisRunAdapter;
import buaa.msasca.sca.infra.persistence.jpa.adapter.JpaProjectAdapter;
import buaa.msasca.sca.infra.persistence.jpa.adapter.JpaProjectVersionSourceCacheAdapter;

import buaa.msasca.sca.infra.persistence.jpa.mapper.AnalysisRunMapper;
import buaa.msasca.sca.infra.persistence.jpa.mapper.ProjectMapper;
import buaa.msasca.sca.infra.persistence.jpa.mapper.SourceCacheMapper;

import buaa.msasca.sca.infra.persistence.jpa.repository.AnalysisRunJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.ProjectJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.ProjectVersionJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.ProjectVersionSourceCacheJpaRepository;

/**
 * Persistence Wiring
 * - JPA Repository + Mapper + Adapter 조립
 * - core의 Port 인터페이스에 infra 구현체를 바인딩한다.
 * - UseCase/Controller 빈은 여기서 만들지 않는다.
 */
@Configuration
public class PersistenceWiringConfig {

    // ===== Mappers =====

    /** ProjectEntity <-> Domain 변환을 위한 Mapper 빈을 생성한다. */
    @Bean
    public ProjectMapper projectMapper() {
        return new ProjectMapper();
    }

    /** SourceCacheEntity <-> Domain 변환을 위한 Mapper 빈을 생성한다. */
    @Bean
    public SourceCacheMapper sourceCacheMapper() {
        return new SourceCacheMapper();
    }

    /** AnalysisRunEntity <-> Domain 변환을 위한 Mapper 빈을 생성한다. */
    @Bean
    public AnalysisRunMapper analysisRunMapper() {
        return new AnalysisRunMapper();
    }

    // ===== Adapters (Ports) =====

    /**
     * ProjectPort를 JPA 기반 Adapter로 바인딩한다.
     *
     * @param repo ProjectJpaRepository
     * @param mapper ProjectMapper
     * @return ProjectPort
     */
    @Bean
    public ProjectPort projectPort(ProjectJpaRepository repo, ProjectMapper mapper) {
        return new JpaProjectAdapter(repo, mapper);
    }

    /**
     * SourceCache Adapter를 단일 빈으로 생성한다.
     * (Port / CommandPort가 같은 구현체를 공유하도록)
     *
     * @param repo SourceCache repository
     * @param pvRepo ProjectVersion repository
     * @param mapper SourceCacheMapper
     * @return JpaProjectVersionSourceCacheAdapter
     */
    @Bean
    public JpaProjectVersionSourceCacheAdapter sourceCacheAdapter(
        ProjectVersionSourceCacheJpaRepository repo,
        ProjectVersionJpaRepository pvRepo,
        SourceCacheMapper mapper
    ) {
        return new JpaProjectVersionSourceCacheAdapter(repo, pvRepo, mapper);
    }

    /**
     * SourceCache 조회 Port를 Adapter에 바인딩한다.
     *
     * @param adapter sourceCacheAdapter
     * @return ProjectVersionSourceCachePort
     */
    @Bean
    public ProjectVersionSourceCachePort sourceCachePort(JpaProjectVersionSourceCacheAdapter adapter) {
        return adapter;
    }

    /**
     * SourceCache 쓰기 CommandPort를 Adapter에 바인딩한다.
     *
     * @param adapter sourceCacheAdapter
     * @return ProjectVersionSourceCacheCommandPort
     */
    @Bean
    public ProjectVersionSourceCacheCommandPort sourceCacheCommandPort(JpaProjectVersionSourceCacheAdapter adapter) {
        return adapter;
    }

    /**
     * AnalysisRunPort를 JPA 기반 Adapter로 바인딩한다.
     *
     * @param runRepo analysis_run repository
     * @param pvRepo project_version repository
     * @param mapper AnalysisRunMapper
     * @return AnalysisRunPort
     */
    @Bean
    public AnalysisRunPort analysisRunPort(
        AnalysisRunJpaRepository runRepo,
        ProjectVersionJpaRepository pvRepo,
        AnalysisRunMapper mapper
    ) {
        return new JpaAnalysisRunAdapter(runRepo, pvRepo, mapper);
    }
}