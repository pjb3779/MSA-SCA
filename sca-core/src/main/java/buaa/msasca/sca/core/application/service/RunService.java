package buaa.msasca.sca.core.application.service;

import buaa.msasca.sca.core.application.usecase.GetRunUseCase;
import buaa.msasca.sca.core.domain.model.AnalysisRun;
import buaa.msasca.sca.core.port.out.persistence.AnalysisRunCommandPort;

import java.util.Optional;

public class RunService implements GetRunUseCase {

  private final AnalysisRunCommandPort analysisRunPort;

  public RunService(AnalysisRunCommandPort analysisRunPort) {
    this.analysisRunPort = analysisRunPort;
  }

  @Override
  public Optional<AnalysisRun> get(String runId) {
    Long id = parseId(runId);
    return analysisRunPort.findById(id);
  }

  private Long parseId(String runId) {
    try {
      return Long.parseLong(runId);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("runId must be a number: " + runId);
    }
  }
}