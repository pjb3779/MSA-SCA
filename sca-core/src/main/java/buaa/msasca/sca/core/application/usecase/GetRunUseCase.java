package buaa.msasca.sca.core.application.usecase;

import java.util.Optional;

import buaa.msasca.sca.core.domain.model.AnalysisRun;

public interface GetRunUseCase {
  Optional<AnalysisRun> get(String runId);
}