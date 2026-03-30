package buaa.msasca.sca.core.port.in;

import java.util.Optional;

import buaa.msasca.sca.core.domain.model.AnalysisRun;

public interface GetAnalysisRunUseCase {
    //존재하지 않으면 Optional.empty() 반환
    Optional<AnalysisRun> findById(Long analysisRunId);
    // project_version 기준 최신 run 조회(없으면 Optional.empty())
    Optional<AnalysisRun> findLatestByProjectVersionId(Long projectVersionId);
}
