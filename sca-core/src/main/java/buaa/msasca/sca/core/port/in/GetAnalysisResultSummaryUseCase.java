package buaa.msasca.sca.core.port.in;

import buaa.msasca.sca.core.port.out.persistence.UnifiedResultQueryPort.AnalysisResultSummaryView;

public interface GetAnalysisResultSummaryUseCase {
  AnalysisResultSummaryView getSummary(Long analysisRunId);
}

