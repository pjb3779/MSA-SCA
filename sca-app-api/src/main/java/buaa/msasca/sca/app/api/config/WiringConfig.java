package buaa.msasca.sca.app.api.config;

import buaa.msasca.sca.core.application.pipeline.PipelineService;
import buaa.msasca.sca.core.application.service.ProjectService;
import buaa.msasca.sca.core.application.service.RunService;
import buaa.msasca.sca.core.application.usecase.CreateProjectUseCase;
import buaa.msasca.sca.core.application.usecase.GetRunUseCase;
import buaa.msasca.sca.core.application.usecase.RequestRunUseCase;
import buaa.msasca.sca.core.port.out.*;
import buaa.msasca.sca.core.port.out.tool.AgentPort;
import buaa.msasca.sca.core.port.out.tool.CodeqlPort;
import buaa.msasca.sca.core.port.out.tool.MscanPort;
import buaa.msasca.sca.core.port.out.tool.RunnerPort;
import buaa.msasca.sca.core.port.out.tool.StoragePort;
import buaa.msasca.sca.infra.persistence.jpa.adapter.JpaProjectAdapter;
import buaa.msasca.sca.infra.persistence.jpa.adapter.JpaRunAdapter;
import buaa.msasca.sca.infra.persistence.jpa.mapper.ProjectMapper;
import buaa.msasca.sca.infra.persistence.jpa.mapper.RunMapper;
import buaa.msasca.sca.infra.persistence.jpa.repository.AnalysisRunJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.ProjectJpaRepository;
import buaa.msasca.sca.infra.runner.LocalProcessRunnerAdapter;
import buaa.msasca.sca.infra.storage.NoopStorageAdapter;
import buaa.msasca.sca.tool.agent.AgentPortAdapter;
import buaa.msasca.sca.tool.codeql.CodeqlPortAdapter;
import buaa.msasca.sca.tool.mscan.MscanPortAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WiringConfig {

  @Bean
  public ProjectMapper projectMapper() { return new ProjectMapper(); }

  @Bean
  public RunMapper runMapper() { return new RunMapper(); }

  @Bean
  public ProjectPort projectPort(ProjectJpaRepository repo, ProjectMapper mapper) {
    return new JpaProjectAdapter(repo, mapper);
  }

  @Bean
  public RunPort runPort(AnalysisRunJpaRepository repo, RunMapper mapper) {
    return new JpaRunAdapter(repo, mapper);
  }

  @Bean
  public StoragePort storagePort() { return new NoopStorageAdapter(); }

  @Bean
  public RunnerPort runnerPort() { return new LocalProcessRunnerAdapter(); }

  @Bean
  public CodeqlPort codeqlPort() { return new CodeqlPortAdapter(); }

  @Bean
  public AgentPort agentPort() { return new AgentPortAdapter(); }

  @Bean
  public MscanPort mscanPort() { return new MscanPortAdapter(); }

  @Bean
  public CreateProjectUseCase createProjectUseCase(ProjectPort projectPort) {
    return new ProjectService(projectPort);
  }

  @Bean
  public RunService runService(ProjectPort projectPort, RunPort runPort) {
    return new RunService(projectPort, runPort);
  }

  @Bean
  public RequestRunUseCase requestRunUseCase(RunService svc) { return svc; }

  @Bean
  public GetRunUseCase getRunUseCase(RunService svc) { return svc; }

  @Bean
  public PipelineService pipelineService(RunPort runPort, CodeqlPort codeqlPort, AgentPort agentPort, MscanPort mscanPort) {
    return new PipelineService(runPort, codeqlPort, agentPort, mscanPort);
  }
}
