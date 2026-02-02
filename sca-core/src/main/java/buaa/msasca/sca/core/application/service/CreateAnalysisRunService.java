package buaa.msasca.sca.core.application.service;

import buaa.msasca.sca.core.domain.model.AnalysisRun;
import buaa.msasca.sca.core.port.in.CreateAnalysisRunUseCase;
import buaa.msasca.sca.core.port.out.persistence.AnalysisRunPort;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionSourceCachePort;

public class CreateAnalysisRunService implements CreateAnalysisRunUseCase {
    private final AnalysisRunPort analysisRunPort;
    private final ProjectVersionSourceCachePort sourceCachePort;

    public CreateAnalysisRunService(AnalysisRunPort analysisRunPort, ProjectVersionSourceCachePort sourceCachePort) {
        this.analysisRunPort = analysisRunPort;
        this.sourceCachePort = sourceCachePort;
    }

    @Override
    public AnalysisRun handle(Command command) {
        if (command.requireSourceCache()) {
        sourceCachePort.findValidByProjectVersionId(command.projectVersionId())
            .orElseThrow(() -> new IllegalStateException(
                "Source cache is not ready for projectVersionId=" + command.projectVersionId()
            ));
        }

        return analysisRunPort.create(
            command.projectVersionId(),
            command.configJson(),
            command.triggeredBy()
        );
    }
}
