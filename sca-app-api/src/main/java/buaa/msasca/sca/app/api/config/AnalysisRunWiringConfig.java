package buaa.msasca.sca.app.api.config;

import buaa.msasca.sca.core.application.service.CreateAnalysisRunService;
import buaa.msasca.sca.core.application.service.GetAnalysisResultSummaryService;
import buaa.msasca.sca.core.application.service.GetAnalysisRunService;
import buaa.msasca.sca.core.application.service.GetAnalysisRunStagesService;
import buaa.msasca.sca.core.application.service.GetAnalysisVisualizationService;
import buaa.msasca.sca.core.port.in.CreateAnalysisRunUseCase;
import buaa.msasca.sca.core.port.in.GetAnalysisResultSummaryUseCase;
import buaa.msasca.sca.core.port.in.GetAnalysisRunUseCase;
import buaa.msasca.sca.core.port.in.GetAnalysisRunStagesUseCase;
import buaa.msasca.sca.core.port.in.GetAnalysisVisualizationUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import buaa.msasca.sca.core.port.out.persistence.AnalysisRunCommandPort;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionSourceCachePort;
import buaa.msasca.sca.core.port.out.persistence.ToolRunPort;
import buaa.msasca.sca.core.port.out.persistence.UnifiedResultQueryPort;


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

    @Bean
    public GetAnalysisResultSummaryUseCase getAnalysisResultSummaryUseCase(
        UnifiedResultQueryPort unifiedResultQueryPort
    ) {
        return new GetAnalysisResultSummaryService(unifiedResultQueryPort);
    }

    @Bean
    public GetAnalysisRunStagesUseCase getAnalysisRunStagesUseCase(
        AnalysisRunCommandPort analysisRunPort,
        ToolRunPort toolRunPort
    ) {
        return new GetAnalysisRunStagesService(analysisRunPort, toolRunPort);
    }

    @Bean
    public GetAnalysisVisualizationUseCase getAnalysisVisualizationUseCase(
        UnifiedResultQueryPort unifiedResultQueryPort
    ) {
        return new GetAnalysisVisualizationService(unifiedResultQueryPort);
    }
}