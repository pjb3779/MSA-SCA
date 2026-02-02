package buaa.msasca.sca.core.port.in;

import java.util.Optional;

import buaa.msasca.sca.core.domain.model.AnalysisRun;

public interface GetAnalysisRunUseCase {
    Optional<AnalysisRun> findById(Long analysisRunId);
}
