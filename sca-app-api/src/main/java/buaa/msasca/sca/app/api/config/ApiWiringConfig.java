package buaa.msasca.sca.app.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import buaa.msasca.sca.core.application.service.CreateAnalysisRunService;
import buaa.msasca.sca.core.application.service.CreateProjectService;
import buaa.msasca.sca.core.application.service.GetAnalysisRunService;
import buaa.msasca.sca.core.application.service.GetProjectService;
import buaa.msasca.sca.core.application.service.GetSourceCacheService;
import buaa.msasca.sca.core.application.service.PrepareSourceCacheService;
import buaa.msasca.sca.core.application.usecase.CreateProjectUseCase;
import buaa.msasca.sca.core.application.usecase.GetProjectUseCase;
import buaa.msasca.sca.core.port.in.CreateAnalysisRunUseCase;
import buaa.msasca.sca.core.port.in.GetAnalysisRunUseCase;
import buaa.msasca.sca.core.port.in.GetSourceCacheUseCase;
import buaa.msasca.sca.core.port.in.PrepareSourceCacheUseCase;
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
 * - JPA 기반 Persistence Adapter들을 Port 인터페이스에 연결
 * - API Controller가 인프라 구현체를 직접 알지 않도록 중간에서 Wiring 담당 
 * - Controller → UseCase → Port → Adapter 흐름을 명확히 분리
 */
@Configuration
public class ApiWiringConfig {

  // ===== Mappers =====

  /** ProjectEntity <-> Domain 변환을 위한 Mapper 빈을 생성한다. */
  @Bean
  public ProjectMapper projectMapper() { return new ProjectMapper(); }

  /** SourceCacheEntity <-> Domain 변환을 위한 Mapper 빈을 생성한다. */
  @Bean
  public SourceCacheMapper sourceCacheMapper() { return new SourceCacheMapper(); }

  /** AnalysisRunEntity <-> Domain 변환을 위한 Mapper 빈을 생성한다. */
  @Bean
  public AnalysisRunMapper analysisRunMapper() { return new AnalysisRunMapper(); }

  // ===== Project (Port/Adapter/UseCase) =====

  /** ProjectPort를 JPA 기반 Adapter로 바인딩한다. */
  @Bean
  public ProjectPort projectPort(ProjectJpaRepository repo, ProjectMapper mapper) {
    return new JpaProjectAdapter(repo, mapper);
  }

  /** 프로젝트 생성 UseCase를 구성한다. */
  @Bean
  public CreateProjectUseCase createProjectUseCase(ProjectPort projectPort) {
    return new CreateProjectService(projectPort);
  }

  /** 프로젝트 조회 UseCase를 구성한다. */
  @Bean
  public GetProjectUseCase getProjectUseCase(ProjectPort projectPort) {
    return new GetProjectService(projectPort);
  }

  // ===== AnalysisRun / SourceCache (Port/Adapter/UseCase) =====

  /** SourceCache 조회 Port를 JPA 기반 Adapter로 바인딩한다. */
  @Bean
  public ProjectVersionSourceCachePort sourceCachePort(
      ProjectVersionSourceCacheJpaRepository repo,
      ProjectVersionJpaRepository pvRepo,
      SourceCacheMapper mapper
  ) {
    return new JpaProjectVersionSourceCacheAdapter(repo, pvRepo, mapper);
  }

  /** SourceCache 쓰기 CommandPort를 JPA 기반 Adapter로 바인딩한다. */
  @Bean
  public ProjectVersionSourceCacheCommandPort sourceCacheCommandPort(
      ProjectVersionSourceCacheJpaRepository repo,
      ProjectVersionJpaRepository pvRepo,
      SourceCacheMapper mapper
  ) {
    return new JpaProjectVersionSourceCacheAdapter(repo, pvRepo, mapper);
  }

  /** AnalysisRunPort를 JPA 기반 Adapter로 바인딩한다. */
  @Bean
  public AnalysisRunPort analysisRunPort(
      AnalysisRunJpaRepository runRepo,
      ProjectVersionJpaRepository pvRepo,
      AnalysisRunMapper mapper
  ) {
    return new JpaAnalysisRunAdapter(runRepo, pvRepo, mapper);
  }

  /** 분석 런 생성 UseCase를 구성한다. */
  @Bean
  public CreateAnalysisRunUseCase createAnalysisRunUseCase(
      AnalysisRunPort analysisRunPort,
      ProjectVersionSourceCachePort sourceCachePort
  ) {
    return new CreateAnalysisRunService(analysisRunPort, sourceCachePort);
  }

  /** 분석 런 조회 UseCase를 구성한다. */
  @Bean
  public GetAnalysisRunUseCase getAnalysisRunUseCase(AnalysisRunPort analysisRunPort) {
    return new GetAnalysisRunService(analysisRunPort);
  }

  /** SourceCache 수동 준비 UseCase를 구성한다. */
  @Bean
  public PrepareSourceCacheUseCase prepareSourceCacheUseCase(ProjectVersionSourceCacheCommandPort commandPort) {
    return new PrepareSourceCacheService(commandPort);
  }

  /** SourceCache 조회 UseCase를 구성한다. */
  @Bean
  public GetSourceCacheUseCase getSourceCacheUseCase(ProjectVersionSourceCachePort port) {
    return new GetSourceCacheService(port);
  }
}