package buaa.msasca.sca.app.api.config;

import buaa.msasca.sca.core.application.service.CreateProjectService;
import buaa.msasca.sca.core.application.service.CreateProjectVersionFromGitService;
import buaa.msasca.sca.core.application.service.CreateProjectVersionFromZipService;
import buaa.msasca.sca.core.application.service.EnqueueAnalysisRunOnSourceReadyService;
import buaa.msasca.sca.core.application.service.EnsureMscanGatewayYamlService;
import buaa.msasca.sca.core.application.service.GetMscanGatewayYamlService;
import buaa.msasca.sca.core.application.service.GetProjectService;
import buaa.msasca.sca.core.application.service.RequestMscanOnlyRunService;
import buaa.msasca.sca.core.application.service.UploadMscanGatewayYamlService;
import buaa.msasca.sca.core.application.support.WorkspacePathResolver;
import buaa.msasca.sca.core.application.usecase.CreateProjectUseCase;
import buaa.msasca.sca.core.application.usecase.CreateProjectVersionFromGitUseCase;
import buaa.msasca.sca.core.application.usecase.CreateProjectVersionFromZipUseCase;
import buaa.msasca.sca.core.application.usecase.GetProjectUseCase;
import buaa.msasca.sca.core.port.in.CreateAnalysisRunUseCase;
import buaa.msasca.sca.core.port.in.EnsureMscanGatewayYamlUseCase;
import buaa.msasca.sca.core.port.in.GetMscanGatewayYamlUseCase;
import buaa.msasca.sca.core.port.in.RequestMscanOnlyRunUseCase;
import buaa.msasca.sca.core.port.in.UploadMscanGatewayYamlUseCase;
import buaa.msasca.sca.core.port.out.persistence.AnalysisRunCommandPort;
import buaa.msasca.sca.core.port.out.persistence.MscanGatewayYamlCommandPort;
import buaa.msasca.sca.core.port.out.persistence.MscanGatewayYamlPort;
import buaa.msasca.sca.core.port.out.persistence.ProjectPort;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionCommandPort;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionPort;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionSourceCacheCommandPort;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionSourceCachePort;
import buaa.msasca.sca.core.port.out.tool.RunnerPort;
import buaa.msasca.sca.core.port.out.tool.StoragePort;
import buaa.msasca.sca.infra.runner.LocalProcessRunnerPortAdapter;
import buaa.msasca.sca.infra.storage.local.LocalStoragePortAdapter;

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
    CreateAnalysisRunUseCase createAnalysisRunUseCase
  ) {
    return new EnqueueAnalysisRunOnSourceReadyService(createAnalysisRunUseCase);
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
  
  @Bean
  public UploadMscanGatewayYamlUseCase uploadMscanGatewayYamlUseCase(
      StoragePort storagePort,
      MscanGatewayYamlCommandPort cmd,
      ProjectVersionSourceCachePort cachePort
  ) {
    return new UploadMscanGatewayYamlService(storagePort, cmd, cachePort);
  }

  @Bean
  public GetMscanGatewayYamlUseCase getMscanGatewayYamlUseCase(MscanGatewayYamlPort port) {
    return new GetMscanGatewayYamlService(port);
  }

  @Bean
  public EnsureMscanGatewayYamlUseCase ensureMscanGatewayYamlUseCase(
      MscanGatewayYamlCommandPort commandPort
  ) {
      return new EnsureMscanGatewayYamlService(commandPort);
  }

  // API에서도 임시로 로컬 스토리지 사용(개발 단계)
  @Bean
  @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(StoragePort.class)
  public StoragePort storagePort() {
    return new LocalStoragePortAdapter();
  }


  //test
  @Bean
  public RequestMscanOnlyRunUseCase requestMscanOnlyRunUseCase(
      ProjectVersionSourceCachePort sourceCachePort,
      StoragePort storagePort,
      MscanGatewayYamlCommandPort gatewayYamlCommandPort,
      CreateAnalysisRunUseCase createAnalysisRunUseCase
  ) {
    return new RequestMscanOnlyRunService(sourceCachePort, storagePort, gatewayYamlCommandPort, createAnalysisRunUseCase);
  }
}