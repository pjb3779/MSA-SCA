package buaa.msasca.sca.core.application.service;

import buaa.msasca.sca.core.port.in.GetAnalysisResultSummaryUseCase;
import buaa.msasca.sca.core.port.out.persistence.UnifiedResultQueryPort;
import buaa.msasca.sca.core.port.out.persistence.UnifiedResultQueryPort.AnalysisResultSummaryView;

public class GetAnalysisResultSummaryService implements GetAnalysisResultSummaryUseCase {

  private final UnifiedResultQueryPort unifiedResultQueryPort;

  public GetAnalysisResultSummaryService(UnifiedResultQueryPort unifiedResultQueryPort) {
    this.unifiedResultQueryPort = unifiedResultQueryPort;
  }

  @Override
  public AnalysisResultSummaryView getSummary(Long analysisRunId) {
    return unifiedResultQueryPort.getSummary(analysisRunId);
  }
}

