package buaa.msasca.sca.app.api.config;

import buaa.msasca.sca.core.application.service.CreateProjectService;
import buaa.msasca.sca.core.application.service.CreateProjectVersionFromGitService;
import buaa.msasca.sca.core.application.service.CreateProjectVersionFromZipService;
import buaa.msasca.sca.core.application.service.EnqueueAnalysisRunOnSourceReadyService;
import buaa.msasca.sca.core.application.service.GetProjectService;
import buaa.msasca.sca.core.application.support.WorkspacePathResolver;
import buaa.msasca.sca.core.application.usecase.CreateProjectUseCase;
import buaa.msasca.sca.core.application.usecase.CreateProjectVersionFromGitUseCase;
import buaa.msasca.sca.core.application.usecase.CreateProjectVersionFromZipUseCase;
import buaa.msasca.sca.core.application.usecase.GetProjectUseCase;
import buaa.msasca.sca.core.port.out.persistence.ProjectPort;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionCommandPort;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionPort;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionSourceCacheCommandPort;
import buaa.msasca.sca.core.port.out.tool.RunnerPort;
import buaa.msasca.sca.infra.runner.LocalProcessRunnerPortAdapter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class ApiWiringConfig {

  /** 로컬 실행 RunnerPort */
  @Bean
  public RunnerPort runnerPort() {
    return new LocalProcessRunnerPortAdapter();
  }

  /** workspace path resolver: Windows 경로 */
  @Bean
  public WorkspacePathResolver workspacePathResolver() {
    return new WorkspacePathResolver("C:/msasca");
  }

  /** usecases */
  @Bean
  public CreateProjectUseCase createProjectUseCase(ProjectPort projectPort) {
    return new CreateProjectService(projectPort);
  }

  @Bean
  public GetProjectUseCase getProjectUseCase(ProjectPort projectPort) {
    return new GetProjectService(projectPort);
  }

  @Bean
  public EnqueueAnalysisRunOnSourceReadyService enqueueAnalysisRunOnSourceReadyService(
      buaa.msasca.sca.core.port.out.persistence.AnalysisRunCommandPort analysisRunCommandPort,
      ObjectMapper objectMapper
  ) {
    return new EnqueueAnalysisRunOnSourceReadyService(analysisRunCommandPort, objectMapper);
  }

  @Bean
  public CreateProjectVersionFromGitUseCase createProjectVersionFromGitUseCase(
      ProjectPort projectPort,
      ProjectVersionPort projectVersionPort,
      ProjectVersionSourceCacheCommandPort cacheCommandPort,
      RunnerPort runnerPort,
      WorkspacePathResolver pathResolver,
      EnqueueAnalysisRunOnSourceReadyService enqueueService
  ) {
    return new CreateProjectVersionFromGitService(
        projectPort,
        projectVersionPort,
        cacheCommandPort,
        runnerPort,
        pathResolver,
        enqueueService
    );
  }

  @Bean
  public CreateProjectVersionFromZipUseCase createProjectVersionFromZipUseCase(
      ProjectPort projectPort,
      ProjectVersionPort projectVersionPort,
      ProjectVersionCommandPort projectVersionCommandPort,
      ProjectVersionSourceCacheCommandPort cacheCommandPort,
      WorkspacePathResolver pathResolver,
      RunnerPort runnerPort,
      EnqueueAnalysisRunOnSourceReadyService enqueueService
  ) {
    return new CreateProjectVersionFromZipService(
        projectPort,
        projectVersionPort,
        projectVersionCommandPort,
        cacheCommandPort,
        pathResolver,
        runnerPort,
        enqueueService
    );
  }
}