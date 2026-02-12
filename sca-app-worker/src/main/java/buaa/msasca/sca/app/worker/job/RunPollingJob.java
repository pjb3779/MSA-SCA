package buaa.msasca.sca.app.worker.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import buaa.msasca.sca.core.application.pipeline.PipelineExecutor;
import buaa.msasca.sca.core.domain.enums.RunStatus;
import buaa.msasca.sca.core.port.out.persistence.AnalysisRunCommandPort;

@Component
public class RunPollingJob {
  private static final Logger log = LoggerFactory.getLogger(RunPollingJob.class);

  private final AnalysisRunCommandPort analysisRunCommandPort;
  private final PipelineExecutor pipelineExecutor;

  public RunPollingJob(AnalysisRunCommandPort analysisRunCommandPort, PipelineExecutor pipelineExecutor) {
    this.analysisRunCommandPort = analysisRunCommandPort;
    this.pipelineExecutor = pipelineExecutor;
  }

  /**
   * PENDING analysis_run을 폴링하여 RUNNING으로 클레임하고 파이프라인을 실행한다.
   * 성공 시 DONE, 실패 시 FAILED로 전이한다.
   */
  @Scheduled(fixedDelayString = "${sca.worker.poll-interval-ms:5000}")
  public void poll() {
    log.info("poll tick");
    
    var pending = analysisRunCommandPort.findByStatus(RunStatus.PENDING, 5);

    for (var run : pending) {
      Long runId = run.id();

      try {
        //먼저 RUNNING으로 "클레임"
        var claimed = analysisRunCommandPort.markRunning(runId);

        log.info("Claimed analysisRunId={} projectVersionId={}", claimed.id(), claimed.projectVersionId());

        try {
          pipelineExecutor.execute(claimed.id());

          analysisRunCommandPort.markDone(claimed.id());
          log.info("Done analysisRunId={}", claimed.id());

        } catch (Exception e) {
          analysisRunCommandPort.markFailed(claimed.id());
          log.error("Failed analysisRunId={}", claimed.id(), e);
        }

      } catch (Exception claimFailed) {
        // 이미 다른 워커가 가져갔거나(PENDING이 아니거나), 클레임 실패
        continue;
      }
    }
  }
}