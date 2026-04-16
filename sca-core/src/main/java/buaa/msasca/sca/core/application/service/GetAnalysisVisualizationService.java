package buaa.msasca.sca.core.application.service;

import buaa.msasca.sca.core.port.in.GetAnalysisVisualizationUseCase;
import buaa.msasca.sca.core.port.out.persistence.UnifiedResultQueryPort;
import buaa.msasca.sca.core.port.out.persistence.UnifiedResultQueryPort.SemanticDrilldownView;
import buaa.msasca.sca.core.port.out.persistence.UnifiedResultQueryPort.ServiceGraphView;

public class GetAnalysisVisualizationService implements GetAnalysisVisualizationUseCase {

  private final UnifiedResultQueryPort unifiedResultQueryPort;

  public GetAnalysisVisualizationService(UnifiedResultQueryPort unifiedResultQueryPort) {
    this.unifiedResultQueryPort = unifiedResultQueryPort;
  }

  @Override
  public ServiceGraphView getServiceGraph(Long analysisRunId) {
    return unifiedResultQueryPort.getServiceGraph(analysisRunId);
  }

  @Override
  public SemanticDrilldownView getSemanticDrilldown(Long analysisRunId, String moduleName) {
    return unifiedResultQueryPort.getSemanticDrilldown(analysisRunId, moduleName);
  }
}
