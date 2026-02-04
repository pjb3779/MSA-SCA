package buaa.msasca.sca.app.api.config;

import buaa.msasca.sca.core.application.service.CreateProjectService;
import buaa.msasca.sca.core.application.service.CreateProjectVersionFromGitService;
import buaa.msasca.sca.core.application.service.CreateProjectVersionFromZipService;
import buaa.msasca.sca.core.application.service.GetProjectService;
import buaa.msasca.sca.core.application.support.WorkspacePathResolver;
import buaa.msasca.sca.core.application.usecase.CreateProjectUseCase;
import buaa.msasca.sca.core.application.usecase.CreateProjectVersionFromGitUseCase;
import buaa.msasca.sca.core.application.usecase.CreateProjectVersionFromZipUseCase;
import buaa.msasca.sca.core.application.usecase.GetProjectUseCase;
import buaa.msasca.sca.core.port.out.persistence.ProjectPort;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionCommandPort;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionPort;
import buaa.msasca.sca.core.port.out.tool.RunnerPort;
import buaa.msasca.sca.infra.persistence.jpa.adapter.JpaProjectAdapter;
import buaa.msasca.sca.infra.persistence.jpa.adapter.JpaProjectVersionAdapter;
import buaa.msasca.sca.infra.persistence.jpa.adapter.JpaProjectVersionSourceCacheAdapter;
import buaa.msasca.sca.infra.persistence.jpa.mapper.ProjectMapper;
import buaa.msasca.sca.infra.persistence.jpa.mapper.ProjectVersionViewMapper;
import buaa.msasca.sca.infra.persistence.jpa.mapper.SourceCacheMapper;
import buaa.msasca.sca.infra.persistence.jpa.repository.ProjectJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.ProjectVersionJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.ProjectVersionSourceCacheJpaRepository;
import buaa.msasca.sca.infra.runner.LocalProcessRunnerPortAdapter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiWiringConfig {

    /** mapper beans */
    @Bean public ProjectMapper projectMapper() { return new ProjectMapper(); }
    @Bean public ProjectVersionViewMapper projectVersionMapper() { return new ProjectVersionViewMapper(); }
    @Bean public SourceCacheMapper sourceCacheMapper() { return new SourceCacheMapper(); }

    /** ports(adapters) */
    @Bean
    public ProjectPort projectPort(ProjectJpaRepository repo, ProjectMapper mapper) {
      return new JpaProjectAdapter(repo, mapper);
    }

    @Bean
    public JpaProjectVersionAdapter projectVersionAdapter(
        ProjectVersionJpaRepository repo,
        ProjectJpaRepository projectRepo,
        ProjectVersionViewMapper mapper
    ) {
      return new JpaProjectVersionAdapter(repo, projectRepo, mapper);
    }

    @Bean
    public ProjectVersionPort projectVersionPort(JpaProjectVersionAdapter a) {
      return a;
    }

    @Bean
    public ProjectVersionCommandPort projectVersionCommandPort(JpaProjectVersionAdapter a) {
      return a;
    }

    @Bean
    public RunnerPort runnerPort() {
      return new LocalProcessRunnerPortAdapter();
    }
    
    @Bean
    public JpaProjectVersionSourceCacheAdapter pvSourceCacheAdapter(
        ProjectVersionSourceCacheJpaRepository cacheRepo,
        ProjectVersionJpaRepository pvRepo,
        SourceCacheMapper mapper
    ) {
      return new JpaProjectVersionSourceCacheAdapter(cacheRepo, pvRepo, mapper);
    }

    /** workspace path resolver: Windows 경로로 바꾼 설정과 맞춰서 */
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
    public CreateProjectVersionFromGitUseCase createProjectVersionFromGitUseCase(
        ProjectPort projectPort,
        ProjectVersionPort projectVersionPort,
        JpaProjectVersionSourceCacheAdapter cacheCommandPort,
        RunnerPort runnerPort,
        WorkspacePathResolver pathResolver
    ) {
      return new CreateProjectVersionFromGitService(
          projectPort, projectVersionPort, cacheCommandPort, runnerPort, pathResolver
      );
    }

    @Bean
    public CreateProjectVersionFromZipUseCase createProjectVersionFromZipUseCase(
        ProjectPort projectPort,
        ProjectVersionPort projectVersionPort,
        ProjectVersionCommandPort projectVersionCommandPort,
        JpaProjectVersionSourceCacheAdapter cacheCommandPort,  // ✅ 여기 변경
        WorkspacePathResolver pathResolver
    ) {
      return new CreateProjectVersionFromZipService(
          projectPort, projectVersionPort, projectVersionCommandPort, cacheCommandPort, pathResolver
      );
    }
}