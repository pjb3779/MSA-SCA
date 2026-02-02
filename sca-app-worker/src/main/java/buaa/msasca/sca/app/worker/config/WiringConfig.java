package buaa.msasca.sca.app.worker.config;

import buaa.msasca.sca.core.application.pipeline.PipelineExecutor;
import buaa.msasca.sca.core.port.out.persistence.*;
import buaa.msasca.sca.core.port.out.tool.*;

import buaa.msasca.sca.infra.persistence.jpa.adapter.*;
import buaa.msasca.sca.infra.persistence.jpa.mapper.*;
import buaa.msasca.sca.infra.persistence.jpa.repository.*;

import buaa.msasca.sca.infra.runner.LocalProcessRunnerAdapter;
import buaa.msasca.sca.infra.storage.NoopStorageAdapter;

import buaa.msasca.sca.tool.agent.AgentPortAdapter;
import buaa.msasca.sca.tool.codeql.CodeqlPortAdapter;
import buaa.msasca.sca.tool.mscan.MscanPortAdapter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WiringConfig {

  // =========================================================
  // 1) Mapper Bean
  // =========================================================
  // JPA Entity <-> Domain Model(record) 변환 책임.
  // Adapter에서 repo 결과(Entity)를 core 영역에서 쓰는 Domain 타입으로 바꾸거나,
  // 반대로 저장 시 필요한 값 변환을 도와준다.
  @Bean public ServiceModuleMapper serviceModuleMapper() { return new ServiceModuleMapper(); }
  @Bean public SourceCacheMapper sourceCacheMapper() { return new SourceCacheMapper(); }
  @Bean public AnalysisRunMapper analysisRunMapper() { return new AnalysisRunMapper(); }
  @Bean public ToolRunMapper toolRunMapper() { return new ToolRunMapper(); }
  @Bean public ArtifactMapper artifactMapper() { return new ArtifactMapper(); }

  // =========================================================
  // 2) Tool/Infra Adapter Bean
  // =========================================================
  // core는 "Port(인터페이스)"만 의존한다.
  // 실제 구현(LocalProcessRunner, NoopStorage, CodeQL 실행 등)은 여기에서 바인딩한다.

  // 외부 프로세스 실행(빌드, codeql, mscan 등 커맨드 실행용)
  @Bean
  public RunnerPort runnerPort() {
    return new LocalProcessRunnerAdapter();
  }

  // 스토리지는 아직 미정이라 Noop(임시/메모리) 구현체를 붙여둠.
  // 나중에 S3StorageAdapter 같은 것으로 교체하기 쉬움.
  @Bean
  public StoragePort storagePort() {
    return new NoopStorageAdapter();
  }

  // CodeQL 실행 어댑터(현재는 stub, 나중에 실제 codeql cli 실행 구현)
  @Bean
  public CodeqlPort codeqlPort() {
    return new CodeqlPortAdapter();
  }

  // Agent 실행 어댑터(LLM 기반 sanitizer registry 구축 등)
  @Bean
  public AgentPort agentPort() {
    return new AgentPortAdapter();
  }

  // MScan 실행 어댑터(현재는 stub)
  @Bean
  public MscanPort mscanPort() {
    return new MscanPortAdapter();
  }

  // =========================================================
  // 3) Persistence Adapter Bean (JPA 기반)
  // =========================================================
  // Port(인터페이스) -> JPA Adapter(구현체) 연결
  // core는 Port만 의존하지만, 실제 데이터는 DB(JPA)로 저장되므로 Adapter가 필요함.

  @Bean
  public ServiceModulePort serviceModulePort(
      ServiceModuleJpaRepository repo,
      ServiceModuleMapper mapper
  ) {
    // "서비스 모듈 조회" 포트를 JPA 구현체로 연결
    return new JpaServiceModuleAdapter(repo, mapper);
  }

  @Bean
  public ProjectVersionSourceCachePort projectVersionSourceCachePort(
      ProjectVersionSourceCacheJpaRepository repo,
      SourceCacheMapper mapper
  ) {
    // "소스 캐시 조회" 포트를 JPA 구현체로 연결
    return new JpaProjectVersionSourceCacheAdapter(repo, mapper);
  }

  @Bean
  public AnalysisRunPort analysisRunPort(
      AnalysisRunJpaRepository runRepo,
      ProjectVersionJpaRepository pvRepo,
      AnalysisRunMapper mapper
  ) {
    // analysis_run 생성/상태변경/조회 포트를 JPA 구현체로 연결
    return new JpaAnalysisRunAdapter(runRepo, pvRepo, mapper);
  }

  // =========================================================
  // 4) ToolRun Adapter는 CommandPort + Query/StatePort 둘 다 구현
  // =========================================================
  // JpaToolRunAdapter는
  // - tool_run 생성(createXxxRun) : ToolRunCommandPort 역할
  // - tool_run 상태 전이(markRunning/done/fail) : ToolRunPort 역할
  // 이 둘을 동시에 구현한다.
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

  // 동일한 구현체를 두 개의 포트 타입으로 "노출"시킴.
  // PipelineExecutor는 ToolRunCommandPort / ToolRunPort를 각각 받기 때문에 이렇게 분리해서 제공.
  @Bean
  public ToolRunCommandPort toolRunCommandPort(JpaToolRunAdapter a) { return a; }

  @Bean
  public ToolRunPort toolRunPort(JpaToolRunAdapter a) { return a; }

  @Bean
  public AnalysisArtifactPort artifactPort(
      AnalysisArtifactJpaRepository ar,
      ToolRunJpaRepository tr,
      ArtifactMapper mapper
  ) {
    // analysis_artifact 저장 포트를 JPA 구현체로 연결
    return new JpaArtifactAdapter(ar, tr, mapper);
  }

  // =========================================================
  // 5) PipelineExecutor Bean
  // =========================================================
  // 파이프라인 오케스트레이션 유스케이스.
  // 필요한 포트들을 모두 주입받아 실행한다.
  // (즉, PipelineExecutor는 구현체를 모르고 Port만 알기 때문에 테스트/교체가 쉬움)
  @Bean
  public PipelineExecutor pipelineExecutor(
      AnalysisRunPort analysisRunPort,
      ServiceModulePort serviceModulePort,
      ProjectVersionSourceCachePort sourceCachePort,
      ToolRunCommandPort toolRunCommandPort,
      ToolRunPort toolRunPort,
      AnalysisArtifactPort artifactPort,
      RunnerPort runnerPort,
      StoragePort storagePort,
      CodeqlPort codeqlPort,
      AgentPort agentPort,
      MscanPort mscanPort
  ) {
    return new PipelineExecutor(
        analysisRunPort,
        serviceModulePort,
        sourceCachePort,
        toolRunCommandPort,
        toolRunPort,
        artifactPort,
        runnerPort,
        storagePort,
        codeqlPort,
        agentPort,
        mscanPort
    );
  }
}