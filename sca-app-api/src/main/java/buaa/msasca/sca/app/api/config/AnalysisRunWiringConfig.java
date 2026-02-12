package buaa.msasca.sca.app.api.config;

import buaa.msasca.sca.core.application.service.CreateAnalysisRunService;
import buaa.msasca.sca.core.application.service.GetAnalysisRunService;
import buaa.msasca.sca.core.port.in.CreateAnalysisRunUseCase;
import buaa.msasca.sca.core.port.in.GetAnalysisRunUseCase;
import buaa.msasca.sca.infra.persistence.jpa.mapper.AnalysisRunMapper;
import buaa.msasca.sca.infra.persistence.jpa.repository.AnalysisRunJpaRepository;
import buaa.msasca.sca.infra.persistence.jpa.repository.ProjectVersionJpaRepository;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import buaa.msasca.sca.core.port.out.persistence.AnalysisRunCommandPort;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionSourceCachePort;
import buaa.msasca.sca.infra.persistence.jpa.adapter.JpaAnalysisRunAdapter;


//컨트롤러 테스트용 컨피그
@Configuration
public class AnalysisRunWiringConfig {

    /**
     * UseCase Bean.
     */
    @Bean
    public CreateAnalysisRunUseCase createAnalysisRunUseCase(
        AnalysisRunCommandPort cmdPort,
        ProjectVersionSourceCachePort cachePort
    ) {
        return new CreateAnalysisRunService(cmdPort, cachePort);
    }

    /**
     * Get 빈
     */
    @Bean
    public GetAnalysisRunUseCase getAnalysisRunUseCase(
        AnalysisRunCommandPort analysisRunPort
    ) {
        return new GetAnalysisRunService(analysisRunPort);
    }
}