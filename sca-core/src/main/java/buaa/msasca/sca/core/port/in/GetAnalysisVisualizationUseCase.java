package buaa.msasca.sca.core.port.in;

import buaa.msasca.sca.core.port.out.persistence.UnifiedResultQueryPort.SemanticDrilldownView;
import buaa.msasca.sca.core.port.out.persistence.UnifiedResultQueryPort.ServiceGraphView;

/**
 * 분석 결과 시각화: 서비스 그래프(베이스+오버레이) 및 서비스 내부 의미 계층 드릴다운.
 */
public interface GetAnalysisVisualizationUseCase {

  ServiceGraphView getServiceGraph(Long analysisRunId);

  SemanticDrilldownView getSemanticDrilldown(Long analysisRunId, String moduleName);
}
