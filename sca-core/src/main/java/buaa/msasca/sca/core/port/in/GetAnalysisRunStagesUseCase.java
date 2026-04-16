package buaa.msasca.sca.core.port.in;

import buaa.msasca.sca.core.domain.enums.RunStatus;

public interface GetAnalysisRunStagesUseCase {
  StagesView getStages(Long analysisRunId);

  record StagesView(
      Long analysisRunId,
      RunStatus analysisRunStatus,
      StageView agentPrefilter,
      StageView build,
      StageView codeql,
      StageView agentKnowledge,
      StageView mscan,
      StageView merge,
      String currentEngine
  ) {}

  record StageView(RunStatus status, String latestToolRunId) {}
}

