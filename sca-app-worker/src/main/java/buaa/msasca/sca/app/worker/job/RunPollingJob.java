package buaa.msasca.sca.app.worker.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import buaa.msasca.sca.core.application.pipeline.PipelineExecutor;
import buaa.msasca.sca.core.domain.enums.RunStatus;
import buaa.msasca.sca.core.port.out.persistence.AnalysisRunPort;

@Component
public class RunPollingJob {
  private static final Logger log = LoggerFactory.getLogger(RunPollingJob.class);

  private final AnalysisRunPort analysisRunPort;
  private final PipelineExecutor pipelineExecutor;

  public RunPollingJob(AnalysisRunPort analysisRunPort, PipelineExecutor pipelineExecutor) {
    this.analysisRunPort = analysisRunPort;
    this.pipelineExecutor = pipelineExecutor;
  }

  @Scheduled(fixedDelayString = "5000")
  public void poll() {
    var pending = analysisRunPort.findByStatus(RunStatus.PENDING, 5);
    for (var run : pending) {
      log.info("Picking analysisRunId={} projectVersionId={}", run.id(), run.projectVersionId());
      pipelineExecutor.execute(run.id());
    }
  }
}