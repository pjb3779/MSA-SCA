package buaa.msasca.sca.app.api.dto;

import buaa.msasca.sca.core.domain.enums.RunStatus;

public record AnalysisRunStagesResponse(
    Long analysisRunId,
    RunStatus analysisRunStatus,
    Stage agentPrefilter,
    Stage build,
    Stage codeql,
    Stage agentKnowledge,
    Stage mscan,
    Stage merge,
    String currentEngine
) {
  public record Stage(RunStatus status, String latestToolRunId) {}
}

