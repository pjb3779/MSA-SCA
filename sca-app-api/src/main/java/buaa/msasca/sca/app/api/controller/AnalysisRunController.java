package buaa.msasca.sca.app.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import buaa.msasca.sca.app.api.dto.AnalysisRunResponse;
import buaa.msasca.sca.app.api.dto.CreateAnalysisRunRequest;
import buaa.msasca.sca.core.port.in.CreateAnalysisRunUseCase;
import buaa.msasca.sca.core.port.in.GetAnalysisRunUseCase;

@RestController
@RequestMapping("/api")
public class AnalysisRunController {
    
    private final CreateAnalysisRunUseCase createUseCase;
    private final GetAnalysisRunUseCase getUseCase;
    
    public AnalysisRunController(CreateAnalysisRunUseCase createUseCase, GetAnalysisRunUseCase getUseCase) {
        this.createUseCase = createUseCase;
        this.getUseCase = getUseCase;
    }

    /**
     * analysis_run 생성
     * 흐름:
     * 1. project_version_id 유효성 확인
     * 2. (옵션) source cache 존재 여부 검증
     * 3. analysis_run을 PENDING 상태로 생성
     *
     * @param projectVersionId 분석 대상 프로젝트 버전 ID
     * @param req 요청 바디 (config, triggeredBy 등)
     * @return 생성된 analysis_run 정보
     */
    public AnalysisRunResponse create(
        @PathVariable Long projectVersionId,
        @RequestBody CreateAnalysisRunRequest req
    ) {
        boolean requireCache = (req.requireSourceCache() == null) ? true : req.requireSourceCache();

        var run = createUseCase.handle(new CreateAnalysisRunUseCase.Command(
            projectVersionId,
            req.configJson(),
            req.triggeredBy(),
            requireCache
        ));

        return new AnalysisRunResponse(
            run.id(),
            run.projectVersionId(),
            run.status(),
            run.startedAt(),
            run.finishedAt(),
            run.createdAt(),
            run.updatedAt()
        );
    }

    /**
     * analysis_run 단건 조회
     *
     * @param analysisRunId analysis_run ID
     * @return analysis_run 상태 정보
     */
    @GetMapping("/analysis-runs/{analysisRunId}")
    public AnalysisRunResponse get(@PathVariable Long analysisRunId) {
        var run = getUseCase.findById(analysisRunId)
            .orElseThrow(() -> new IllegalArgumentException("analysis_run not found: " + analysisRunId));

        return new AnalysisRunResponse(
            run.id(),
            run.projectVersionId(),
            run.status(),
            run.startedAt(),
            run.finishedAt(),
            run.createdAt(),
            run.updatedAt()
        );
    }
}