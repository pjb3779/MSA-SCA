package buaa.msasca.sca.app.api.config;

import buaa.msasca.sca.core.application.service.PrepareSourceCacheAutoService;
import buaa.msasca.sca.core.port.in.PrepareSourceCacheAutoUseCase;
import buaa.msasca.sca.core.port.out.persistence.*;
import buaa.msasca.sca.core.port.out.tool.RunnerPort;
import buaa.msasca.sca.infra.persistence.jpa.adapter.JpaProjectVersionAdapter;
import buaa.msasca.sca.infra.persistence.jpa.adapter.JpaProjectVersionSourceCacheAdapter;
import buaa.msasca.sca.infra.persistence.jpa.mapper.ProjectVersionViewMapper;
import buaa.msasca.sca.infra.persistence.jpa.mapper.SourceCacheMapper;
import buaa.msasca.sca.infra.persistence.jpa.repository.ProjectVersionJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.ProjectVersionSourceCacheJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SourceCacheAutoWiringConfig {

    @Bean public ProjectVersionViewMapper projectVersionViewMapper() { return new ProjectVersionViewMapper(); }
    @Bean public SourceCacheMapper sourceCacheMapper() { return new SourceCacheMapper(); }

    @Bean
    public ProjectVersionPort projectVersionPort(ProjectVersionJpaRepository repo, ProjectVersionViewMapper mapper) {
        return new JpaProjectVersionAdapter(repo, mapper);
    }

    @Bean
    public JpaProjectVersionSourceCacheAdapter sourceCacheAdapter(
        ProjectVersionSourceCacheJpaRepository cacheRepo,
        ProjectVersionJpaRepository pvRepo,
        SourceCacheMapper mapper
    ) {
        return new JpaProjectVersionSourceCacheAdapter(cacheRepo, pvRepo, mapper);
    }

    @Bean public ProjectVersionSourceCachePort sourceCachePort(JpaProjectVersionSourceCacheAdapter a) { return a; }
    @Bean public ProjectVersionSourceCacheCommandPort sourceCacheCommandPort(JpaProjectVersionSourceCacheAdapter a) { return a; }

    @Bean
    public PrepareSourceCacheAutoUseCase prepareSourceCacheAutoUseCase(
        ProjectVersionPort projectVersionPort,
        ProjectVersionSourceCachePort cachePort,
        ProjectVersionSourceCacheCommandPort cacheCommandPort,
        RunnerPort runnerPort,
        @Value("${sca.workspace.base-path:/tmp/msasca-workspace}") String basePath
    ) {
        return new PrepareSourceCacheAutoService(projectVersionPort, cachePort, cacheCommandPort, runnerPort, basePath);
    }
}