package buaa.msasca.sca.core.application.service;

import com.fasterxml.jackson.databind.JsonNode;

import buaa.msasca.sca.core.domain.model.AnalysisRun;
import buaa.msasca.sca.core.port.in.CreateAnalysisRunUseCase;

/**
 * source cache 준비 완료 후 analysis_run을 자동 생성(패턴 B).
 * - 이미 active run(PENDING/RUNNING)이 있으면 생성하지 않고 "skipped" 반환
 */
public class EnqueueAnalysisRunOnSourceReadyService {

    private final CreateAnalysisRunUseCase createAnalysisRunUseCase;

    public EnqueueAnalysisRunOnSourceReadyService(CreateAnalysisRunUseCase createAnalysisRunUseCase) {
        this.createAnalysisRunUseCase = createAnalysisRunUseCase;
    }

    public Result enqueueIfAbsent(Long projectVersionId, JsonNode configJson, String triggeredBy) {
        try {
        AnalysisRun created = createAnalysisRunUseCase.handle(new CreateAnalysisRunUseCase.Command(
            projectVersionId,
            configJson,
            triggeredBy,
            true // requireSourceCache
        ));

        if (created == null) {
            return Result.skipped("active run already exists (skipped)");
        }
        return Result.created(created.id());
        } catch (Exception e) {
        return Result.failed("auto-run failed: " + safeMsg(e));
        }
    }

    private String safeMsg(Exception e) {
        return (e.getMessage() == null) ? e.toString() : e.getMessage();
    }

    public record Result(
        Long analysisRunId,
        boolean skipped,
        String errorMessage
    ) {
        public static Result created(Long runId) {
        return new Result(runId, false, null);
        }
        public static Result skipped(String msg) {
        return new Result(null, true, msg);
        }
        public static Result failed(String msg) {
        return new Result(null, false, msg);
        }
    }
}