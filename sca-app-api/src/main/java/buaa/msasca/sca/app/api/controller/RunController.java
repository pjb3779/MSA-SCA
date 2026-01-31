package buaa.msasca.sca.app.api.controller;

import buaa.msasca.sca.core.application.pipeline.PipelineService;
import buaa.msasca.sca.core.application.usecase.GetRunUseCase;
import buaa.msasca.sca.core.application.usecase.RequestRunUseCase;
import buaa.msasca.sca.core.domain.model.run.AnalysisRun;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/runs")
public class RunController {
  private final RequestRunUseCase requestRunUseCase;
  private final GetRunUseCase getRunUseCase;
  private final PipelineService pipelineService;

  public RunController(RequestRunUseCase requestRunUseCase, GetRunUseCase getRunUseCase, PipelineService pipelineService) {
    this.requestRunUseCase = requestRunUseCase;
    this.getRunUseCase = getRunUseCase;
    this.pipelineService = pipelineService;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class RequestRunRequest {
    @NotBlank
    private String projectId;
  }

  @Data
  @AllArgsConstructor
  public static class RequestRunResponse {
    private String runId;
  }

  @PostMapping
  public ResponseEntity<RequestRunResponse> request(@RequestBody @Valid RequestRunRequest req) {
    var res = requestRunUseCase.request(new RequestRunUseCase.Request(req.getProjectId()));
    // MVP convenience: run immediately. In real architecture, worker picks it up.
    // pipelineService.execute(res.runId());
    return ResponseEntity.ok(new RequestRunResponse(res.runId()));
  }

  @GetMapping("/{runId}")
  public ResponseEntity<AnalysisRun> get(@PathVariable String runId) {
    Optional<AnalysisRun> run = getRunUseCase.get(runId);
    return run.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
  }
}
