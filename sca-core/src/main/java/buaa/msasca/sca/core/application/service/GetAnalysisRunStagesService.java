package buaa.msasca.sca.core.application.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import buaa.msasca.sca.core.domain.enums.RunStatus;
import buaa.msasca.sca.core.domain.enums.ToolType;
import buaa.msasca.sca.core.domain.model.ToolRun;
import buaa.msasca.sca.core.port.in.GetAnalysisRunStagesUseCase;
import buaa.msasca.sca.core.port.out.persistence.AnalysisRunCommandPort;
import buaa.msasca.sca.core.port.out.persistence.ToolRunPort;

public class GetAnalysisRunStagesService implements GetAnalysisRunStagesUseCase {

  private final AnalysisRunCommandPort analysisRunPort;
  private final ToolRunPort toolRunPort;

  public GetAnalysisRunStagesService(
      AnalysisRunCommandPort analysisRunPort,
      ToolRunPort toolRunPort
  ) {
    this.analysisRunPort = analysisRunPort;
    this.toolRunPort = toolRunPort;
  }

  @Override
  public StagesView getStages(Long analysisRunId) {
    var run = analysisRunPort.findById(analysisRunId)
        .orElseThrow(() -> new IllegalArgumentException("analysis_run not found: " + analysisRunId));

    List<ToolRun> toolRuns = toolRunPort.findByAnalysisRunId(analysisRunId);
    StageView agentPrefilter = toStageView(findLatestAgentByStage(toolRuns, "prefilter"));
    StageView build = toStageView(findLatestByType(toolRuns, ToolType.BUILD));
    StageView codeql = toStageView(findLatestByType(toolRuns, ToolType.CODEQL));
    StageView agentKnowledge = toStageView(findLatestAgentByStage(toolRuns, "knowledge"));
    StageView mscan = toStageView(findLatestByType(toolRuns, ToolType.MSCAN));
    StageView merge = mergeStage(run.status(), mscan.status());

    String currentEngine = resolveCurrentEngine(toolRuns, run.status(), merge.status());

    return new StagesView(
        run.id(),
        run.status(),
        agentPrefilter,
        build,
        codeql,
        agentKnowledge,
        mscan,
        merge,
        currentEngine
    );
  }

  private Optional<ToolRun> findLatestByType(List<ToolRun> toolRuns, ToolType type) {
    return toolRuns.stream()
        .filter(t -> t.toolType() == type)
        .max(Comparator.comparing(t -> t.createdAt() == null ? java.time.Instant.EPOCH : t.createdAt()));
  }

  private Optional<ToolRun> findLatestAgentByStage(List<ToolRun> toolRuns, String stageName) {
    return toolRuns.stream()
        .filter(t -> t.toolType() == ToolType.AGENT)
        .filter(t -> {
          String stage = t.configJson() == null ? "" : t.configJson().path("stage").asText("");
          return stageName.equalsIgnoreCase(stage);
        })
        .max(Comparator.comparing(t -> t.createdAt() == null ? java.time.Instant.EPOCH : t.createdAt()));
  }

  private StageView toStageView(Optional<ToolRun> tr) {
    if (tr.isEmpty()) return new StageView(RunStatus.PENDING, null);
    ToolRun t = tr.get();
    return new StageView(t.status(), t.id() == null ? null : t.id().toString());
  }

  private StageView mergeStage(RunStatus runStatus, RunStatus mscanStatus) {
    if (runStatus == RunStatus.DONE) return new StageView(RunStatus.DONE, null);
    if (runStatus == RunStatus.FAILED && mscanStatus == RunStatus.DONE) return new StageView(RunStatus.FAILED, null);
    if (runStatus == RunStatus.RUNNING && mscanStatus == RunStatus.DONE) return new StageView(RunStatus.RUNNING, null);
    if (runStatus == RunStatus.FAILED) return new StageView(RunStatus.PENDING, null);
    return new StageView(RunStatus.PENDING, null);
  }

  private String resolveCurrentEngine(List<ToolRun> toolRuns, RunStatus runStatus, RunStatus mergeStatus) {
    Optional<ToolRun> running = toolRuns.stream()
        .filter(t -> t.status() == RunStatus.RUNNING)
        .max(Comparator.comparing(t -> t.updatedAt() == null ? java.time.Instant.EPOCH : t.updatedAt()));
    if (running.isPresent()) {
      ToolRun tr = running.get();
      if (tr.toolType() == ToolType.AGENT) {
        String stage = tr.configJson() == null ? "" : tr.configJson().path("stage").asText("");
        if ("prefilter".equalsIgnoreCase(stage)) return "AGENT_PREFILTER";
        if ("knowledge".equalsIgnoreCase(stage)) return "AGENT_KNOWLEDGE";
      }
      return tr.toolType().name();
    }
    if (mergeStatus == RunStatus.RUNNING) return "MERGE";
    if (runStatus == RunStatus.DONE) return "DONE";
    if (runStatus == RunStatus.FAILED) return "FAILED";
    return "PENDING";
  }
}

