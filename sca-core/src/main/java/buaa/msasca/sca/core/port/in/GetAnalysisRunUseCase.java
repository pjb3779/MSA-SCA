package buaa.msasca.sca.core.port.in;

import java.util.Optional;

import buaa.msasca.sca.core.domain.model.AnalysisRun;

public interface GetAnalysisRunUseCase {
    //존재하지 않으면 Optional.empty() 반환
    Optional<AnalysisRun> findById(Long analysisRunId);
}
