package buaa.msasca.sca.core.application.service;

import buaa.msasca.sca.core.domain.model.AnalysisRun;
import buaa.msasca.sca.core.port.in.CreateAnalysisRunUseCase;
import buaa.msasca.sca.core.port.out.persistence.AnalysisRunCommandPort;
import buaa.msasca.sca.core.port.out.persistence.AnalysisRunCommandPort;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionSourceCachePort;

public class CreateAnalysisRunService implements CreateAnalysisRunUseCase {
    private final AnalysisRunCommandPort analysisRunCommandPort;
    private final ProjectVersionSourceCachePort sourceCachePort;

    public CreateAnalysisRunService(AnalysisRunCommandPort analysisRunCommandPort, ProjectVersionSourceCachePort sourceCachePort) {
        this.analysisRunCommandPort = analysisRunCommandPort;
        this.sourceCachePort = sourceCachePort;
    }

    /**
     * analysis_run 생성의 진입점
     * 캐시 사전 검증 진행후 문제없을시 analysis_run 생성
     */
    @Override
    public AnalysisRun handle(Command command) {
        if (command.requireSourceCache()) {
        sourceCachePort.findValidByProjectVersionId(command.projectVersionId())
            .orElseThrow(() -> new IllegalStateException(
                "Source cache is not ready for projectVersionId=" + command.projectVersionId()
            ));
        }

        if (analysisRunCommandPort.existsActiveRun(command.projectVersionId())) {
            return null;
        }
        return analysisRunCommandPort.createPending(
            command.projectVersionId(),
            command.configJson(),
            command.triggeredBy()
        );
    }
}
