package buaa.msasca.sca.core.application.service;

import java.util.Optional;

import buaa.msasca.sca.core.domain.model.AnalysisRun;
import buaa.msasca.sca.core.port.in.GetAnalysisRunUseCase;
import buaa.msasca.sca.core.port.out.persistence.AnalysisRunPort;

public class GetAnalysisRunService implements GetAnalysisRunUseCase {
    private final AnalysisRunPort analysisRunPort;

    public GetAnalysisRunService(AnalysisRunPort analysisRunPort) {
        this.analysisRunPort = analysisRunPort;
    }

    @Override
    public Optional<AnalysisRun> findById(Long analysisRunId) {
        return analysisRunPort.findById(analysisRunId);
    }
}
