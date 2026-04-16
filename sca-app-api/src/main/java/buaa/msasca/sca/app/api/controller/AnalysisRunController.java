package buaa.msasca.sca.app.api.controller;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import buaa.msasca.sca.app.api.dto.AnalysisFindingsResponse;
import buaa.msasca.sca.app.api.dto.AnalysisGraph2dResponse;
import buaa.msasca.sca.app.api.dto.AnalysisGraph3dResponse;
import buaa.msasca.sca.app.api.dto.AnalysisSemanticDrilldownResponse;
import buaa.msasca.sca.app.api.dto.AnalysisServiceGraphResponse;
import buaa.msasca.sca.app.api.dto.AnalysisServiceSourceGraphResponse;
import buaa.msasca.sca.app.api.dto.AnalysisResultSummaryResponse;
import buaa.msasca.sca.app.api.dto.AnalysisRunResponse;
import buaa.msasca.sca.app.api.dto.AnalysisRunStagesResponse;
import buaa.msasca.sca.app.api.dto.CreateAnalysisRunRequest;
import buaa.msasca.sca.core.domain.enums.Severity;
import buaa.msasca.sca.core.domain.model.AnalysisRun;
import buaa.msasca.sca.core.port.in.CreateAnalysisRunUseCase;
import buaa.msasca.sca.core.port.in.GetAnalysisResultSummaryUseCase;
import buaa.msasca.sca.core.port.in.GetAnalysisRunUseCase;
import buaa.msasca.sca.app.api.support.ServiceSourceGraphBuilder;
import buaa.msasca.sca.core.domain.model.ServiceModule;
import buaa.msasca.sca.core.port.in.GetAnalysisRunStagesUseCase;
import buaa.msasca.sca.core.port.in.GetAnalysisVisualizationUseCase;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionSourceCachePort;
import buaa.msasca.sca.core.port.out.persistence.UnifiedResultQueryPort.SemanticDrilldownView;
import buaa.msasca.sca.core.port.out.persistence.ServiceModulePort;
import buaa.msasca.sca.infra.persistence.jpa.entity.project.ProjectEntity;
import buaa.msasca.sca.infra.persistence.jpa.repository.ProjectVersionJpaRepository;

@RestController
@RequestMapping("/api")
public class AnalysisRunController {
    
    private final CreateAnalysisRunUseCase createUseCase;
    private final GetAnalysisRunUseCase getUseCase;
    private final GetAnalysisResultSummaryUseCase resultSummaryUseCase;
    private final GetAnalysisRunStagesUseCase runStagesUseCase;
    private final GetAnalysisVisualizationUseCase visualizationUseCase;
    private final ServiceModulePort serviceModulePort;
    private final ProjectVersionSourceCachePort sourceCachePort;
    private final ProjectVersionJpaRepository projectVersionJpaRepository;
    
    public AnalysisRunController(
        CreateAnalysisRunUseCase createUseCase,
        GetAnalysisRunUseCase getUseCase,
        GetAnalysisResultSummaryUseCase resultSummaryUseCase,
        GetAnalysisRunStagesUseCase runStagesUseCase,
        GetAnalysisVisualizationUseCase visualizationUseCase,
        ServiceModulePort serviceModulePort,
        ProjectVersionSourceCachePort sourceCachePort,
        ProjectVersionJpaRepository projectVersionJpaRepository
    ) {
        this.createUseCase = createUseCase;
        this.getUseCase = getUseCase;
        this.resultSummaryUseCase = resultSummaryUseCase;
        this.runStagesUseCase = runStagesUseCase;
        this.visualizationUseCase = visualizationUseCase;
        this.serviceModulePort = serviceModulePort;
        this.sourceCachePort = sourceCachePort;
        this.projectVersionJpaRepository = projectVersionJpaRepository;
    }

    private AnalysisRunResponse toRunResponse(AnalysisRun run) {
        Long projectId = null;
        String projectName = null;
        var pvOpt = projectVersionJpaRepository.findById(run.projectVersionId());
        if (pvOpt.isPresent()) {
            ProjectEntity proj = pvOpt.get().getProject();
            if (proj != null) {
                projectId = proj.getId();
                projectName = proj.getName();
            }
        }
        String sourceStoragePath = sourceCachePort.findValidByProjectVersionId(run.projectVersionId())
            .map(c -> c.storagePath())
            .orElse(null);
        return new AnalysisRunResponse(
            run.id(),
            run.projectVersionId(),
            run.status(),
            run.startedAt(),
            run.finishedAt(),
            run.createdAt(),
            run.updatedAt(),
            projectId,
            projectName,
            sourceStoragePath
        );
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
    @PostMapping("/project-versions/{projectVersionId}/analysis-runs")
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

        if (run == null) {
            // active run이 이미 있어서 생성 안 된 케이스
            // 지금 프로젝트 스타일 유지: 예외로 명확히 반환(409가 더 좋지만 일단 IllegalStateException)
            throw new IllegalStateException("Active run already exists for projectVersionId=" + projectVersionId);
        }

        return toRunResponse(run);
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

        return toRunResponse(run);
    }

    /**
     * project_version 기준 최신 analysis_run 조회
     *
     * @param projectVersionId project_version ID
     * @return 최신 analysis_run 상태 정보
     */
    @GetMapping("/project-versions/{projectVersionId}/analysis-runs/latest")
    public AnalysisRunResponse getLatestByProjectVersion(@PathVariable Long projectVersionId) {
        var run = getUseCase.findLatestByProjectVersionId(projectVersionId)
            .orElseThrow(() -> new IllegalArgumentException(
                "latest analysis_run not found for projectVersionId=" + projectVersionId
            ));

        return toRunResponse(run);
    }

    @GetMapping("/analysis-runs/{analysisRunId}/results/summary")
    public AnalysisResultSummaryResponse getResultSummary(@PathVariable Long analysisRunId) {
        var summary = resultSummaryUseCase.getSummary(analysisRunId);
        return mapSummary(summary);
    }

    /**
     * 2D 전파 그래프 조회 (하위 호환).
     * {@link #getServiceGraph(Long)} 와 동일 데이터를 구 형식으로 반환한다.
     */
    @GetMapping("/analysis-runs/{analysisRunId}/results/graph2d")
    public AnalysisGraph2dResponse getGraph2d(@PathVariable Long analysisRunId) {
        var g = visualizationUseCase.getServiceGraph(analysisRunId);
        return new AnalysisGraph2dResponse(
            g.analysisRunId(),
            g.nodes().stream()
                .map(n -> new AnalysisGraph2dResponse.Node(
                    n.id(), n.label(), n.maxSeverity(), n.tainted()))
                .toList(),
            g.edges().stream()
                .map(e -> new AnalysisGraph2dResponse.Edge(
                    e.pathId(),
                    e.source(),
                    e.target(),
                    e.flowCount(),
                    e.flowMaxSeverity(),
                    e.codeqlCount(),
                    e.codeqlMaxSeverity()
                ))
                .toList()
        );
    }

    /**
     * 서비스 그래프: 관측 흐름(베이스) + MScan/CodeQL 지표 + 모듈 체인 와이어링.
     */
    @GetMapping("/analysis-runs/{analysisRunId}/results/service-graph")
    public AnalysisServiceGraphResponse getServiceGraph(@PathVariable Long analysisRunId) {
        var g = visualizationUseCase.getServiceGraph(analysisRunId);
        return new AnalysisServiceGraphResponse(
            g.analysisRunId(),
            g.nodes().stream()
                .map(n -> new AnalysisServiceGraphResponse.Node(
                    n.id(), n.label(), n.maxSeverity(), n.tainted()))
                .toList(),
            g.edges().stream()
                .map(e -> new AnalysisServiceGraphResponse.Edge(
                    e.pathId(),
                    e.source(),
                    e.target(),
                    e.flowCount(),
                    e.flowMaxSeverity(),
                    e.mscanCount(),
                    e.mscanMaxSeverity(),
                    e.codeqlCount(),
                    e.codeqlMaxSeverity()
                ))
                .toList(),
            g.wiringEdges().stream()
                .map(w -> new AnalysisServiceGraphResponse.WiringEdge(w.source(), w.target()))
                .toList()
        );
    }

    /**
     * 서비스 내부 의미 계층 (파일 → 클래스 → 메서드 → 스텝).
     */
    @GetMapping("/analysis-runs/{analysisRunId}/results/semantic-drilldown")
    public AnalysisSemanticDrilldownResponse getSemanticDrilldown(
        @PathVariable Long analysisRunId,
        @RequestParam("moduleName") String moduleName
    ) {
        SemanticDrilldownView v = visualizationUseCase.getSemanticDrilldown(analysisRunId, moduleName);
        return new AnalysisSemanticDrilldownResponse(
            v.analysisRunId(),
            v.moduleName(),
            v.roots().stream().map(AnalysisRunController::mapSemanticNode).toList()
        );
    }

    private static AnalysisSemanticDrilldownResponse.Node mapSemanticNode(
        buaa.msasca.sca.core.port.out.persistence.UnifiedResultQueryPort.SemanticDrilldownNodeView n
    ) {
        return new AnalysisSemanticDrilldownResponse.Node(
            n.id(),
            n.kind(),
            n.label(),
            n.unifiedRecordId(),
            n.stepIndex(),
            n.filePath(),
            n.className(),
            n.methodName(),
            n.lineNumber(),
            n.codeSnippet(),
            n.role(),
            n.vulnerabilityType(),
            n.recordTitle(),
            n.children().stream().map(AnalysisRunController::mapSemanticNode).toList()
        );
    }

    /**
     * 3D 드릴다운 그래프 조회.
     * 서비스 -> 메서드 -> 코드 위치 계층을 노드/엣지로 반환한다.
     */
    @GetMapping("/analysis-runs/{analysisRunId}/results/graph3d")
    public AnalysisGraph3dResponse getGraph3d(@PathVariable Long analysisRunId) {
        var summary = mapSummary(resultSummaryUseCase.getSummary(analysisRunId));

        Map<String, AnalysisGraph3dResponse.Node> nodeMap = new LinkedHashMap<>();
        Map<String, Long> edgeCountMap = new LinkedHashMap<>();

        for (var f : summary.findings()) {
            String service = defaultText(f.sinkModule(), f.sourceModule(), "unknown-service");
            String method = defaultText(f.sinkMethod(), f.sourceMethod(), "unknown-method");
            String codeLoc = defaultText(f.sinkFilePath(), f.sourceFilePath(), "unknown-file")
                + ":" + (f.sinkLine() != null ? f.sinkLine() : (f.sourceLine() != null ? f.sourceLine() : "-"));

            String serviceId = "service:" + service;
            String methodId = "method:" + service + ":" + method;
            String codeId = "code:" + f.unifiedRecordId() + ":" + codeLoc;

            nodeMap.putIfAbsent(serviceId, new AnalysisGraph3dResponse.Node(serviceId, service, "service", f.severity()));
            nodeMap.putIfAbsent(methodId, new AnalysisGraph3dResponse.Node(methodId, method, "method", f.severity()));
            nodeMap.putIfAbsent(codeId, new AnalysisGraph3dResponse.Node(codeId, codeLoc, "code", f.severity()));

            String edge1 = serviceId + "->" + methodId;
            String edge2 = methodId + "->" + codeId;
            edgeCountMap.merge(edge1, 1L, Long::sum);
            edgeCountMap.merge(edge2, 1L, Long::sum);
        }

        List<AnalysisGraph3dResponse.Edge> edges = edgeCountMap.entrySet().stream()
            .map(e -> {
                String[] t = e.getKey().split("->", 2);
                return new AnalysisGraph3dResponse.Edge(t[0], t[1], e.getValue());
            })
            .toList();

        return new AnalysisGraph3dResponse(
            analysisRunId,
            new ArrayList<>(nodeMap.values()),
            edges
        );
    }

    /**
     * 서비스 모듈 소스 디렉터리 트리 그래프 (실제 폴더/파일).
     * 소스 캐시 경로 + service_module.root_path 기준으로 탐색한다.
     */
    @GetMapping("/analysis-runs/{analysisRunId}/results/service-source-graph")
    public AnalysisServiceSourceGraphResponse getServiceSourceGraph(
        @PathVariable Long analysisRunId,
        @RequestParam("moduleName") String moduleName
    ) {
        var run = getUseCase.findById(analysisRunId)
            .orElseThrow(() -> new IllegalArgumentException("analysis_run not found: " + analysisRunId));
        var cacheOpt = sourceCachePort.findValidByProjectVersionId(run.projectVersionId());
        if (cacheOpt.isEmpty()) {
            throw new IllegalStateException("No valid source cache for projectVersionId=" + run.projectVersionId());
        }
        Path sourceRoot = ServiceSourceGraphBuilder.toLocalPath(cacheOpt.get().storagePath());
        ServiceModule module = serviceModulePort.findByProjectVersionId(run.projectVersionId()).stream()
            .filter(m -> moduleName != null && moduleName.equals(m.name()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("service module not found: " + moduleName));

        String rp = module.rootPath() == null ? "" : module.rootPath().replace('\\', '/').replaceFirst("^/+", "");
        Path moduleRoot = rp.isBlank() ? sourceRoot : sourceRoot.resolve(rp);
        return ServiceSourceGraphBuilder.build(analysisRunId, module.name(), moduleRoot);
    }

    /**
     * 파인딩 목록 조회.
     * 서비스/취약점 유형/위험도/path 기준 필터 및 페이지네이션을 지원한다.
     */
    @GetMapping("/analysis-runs/{analysisRunId}/results/findings")
    public AnalysisFindingsResponse getFindings(
        @PathVariable Long analysisRunId,
        @RequestParam(required = false) String service,
        @RequestParam(required = false) String vulnerabilityType,
        @RequestParam(required = false) Severity severity,
        @RequestParam(required = false) String pathId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "30") int size
    ) {
        var summary = mapSummary(resultSummaryUseCase.getSummary(analysisRunId));

        List<AnalysisResultSummaryResponse.Finding> filtered = summary.findings().stream()
            .filter(f -> service == null || service.isBlank()
                || Objects.equals(f.sourceModule(), service)
                || Objects.equals(f.sinkModule(), service)
                || Objects.equals(f.scopeServiceModuleName(), service))
            .filter(f -> vulnerabilityType == null || vulnerabilityType.isBlank()
                || Objects.equals(f.vulnerabilityType(), vulnerabilityType))
            .filter(f -> severity == null || Objects.equals(f.severity(), severity))
            .filter(f -> {
                if (pathId == null || pathId.isBlank()) return true;
                return Objects.equals(pathId, pathId(f.sourceModule(), f.sinkModule()));
            })
            .sorted(Comparator.comparing(AnalysisResultSummaryResponse.Finding::unifiedRecordId, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();

        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 200));
        int from = Math.min(safePage * safeSize, filtered.size());
        int to = Math.min(from + safeSize, filtered.size());
        List<AnalysisResultSummaryResponse.Finding> items = filtered.subList(from, to);

        return new AnalysisFindingsResponse(
            analysisRunId,
            filtered.size(),
            safePage,
            safeSize,
            items
        );
    }

    /**
     * 핵심 경로 export.
     * - format=json: 필터링된 파인딩을 JSON 문자열로 반환
     * - format=csv: 필터링된 파인딩을 CSV 문자열로 반환
     */
    @GetMapping("/analysis-runs/{analysisRunId}/results/export")
    public ResponseEntity<?> exportFindings(
        @PathVariable Long analysisRunId,
        @RequestParam(defaultValue = "json") String format,
        @RequestParam(required = false) String pathId
    ) {
        var findingsResponse = getFindings(analysisRunId, null, null, null, pathId, 0, 10_000);
        var findings = findingsResponse.items();

        if ("csv".equalsIgnoreCase(format)) {
            String header = "recordId,severity,vulnerabilityType,title,scopeServiceModule,sourceModule,sinkModule,sourceMethod,sinkMethod,sourceFile,sourceLine,sinkFile,sinkLine\n";
            String body = findings.stream()
                .map(f -> csvLine(
                    String.valueOf(f.unifiedRecordId()),
                    String.valueOf(f.severity()),
                    f.vulnerabilityType(),
                    f.title(),
                    f.scopeServiceModuleName() == null ? "" : f.scopeServiceModuleName(),
                    f.sourceModule(),
                    f.sinkModule(),
                    f.sourceMethod(),
                    f.sinkMethod(),
                    f.sourceFilePath(),
                    String.valueOf(f.sourceLine()),
                    f.sinkFilePath(),
                    String.valueOf(f.sinkLine())
                ))
                .collect(Collectors.joining("\n"));
            byte[] bytes = (header + body).getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                .contentType(MediaType.valueOf("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=analysis-" + analysisRunId + ".csv")
                .body(bytes);
        }

        return ResponseEntity.ok(findingsResponse);
    }

    private String csvLine(String... values) {
        return java.util.Arrays.stream(values)
            .map(v -> "\"" + (v == null ? "" : v.replace("\"", "\"\"")) + "\"")
            .collect(Collectors.joining(","));
    }

    private String defaultText(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) return first;
        if (second != null && !second.isBlank()) return second;
        return fallback;
    }

    /** source/sink 모듈쌍을 안정적인 경로 식별자로 변환한다. */
    private String pathId(String sourceModule, String sinkModule) {
        return (sourceModule == null ? "unknown" : sourceModule)
            + "->"
            + (sinkModule == null ? "unknown" : sinkModule);
    }

    private Severity moreSevere(Severity base, Severity incoming) {
        if (base == null) return incoming;
        if (incoming == null) return base;
        return severityRank(base) <= severityRank(incoming) ? base : incoming;
    }

    private int severityRank(Severity s) {
        if (s == null) return 99;
        return switch (s) {
            case CRITICAL -> 0;
            case HIGH -> 1;
            case MEDIUM -> 2;
            case LOW -> 3;
        };
    }

    /** core summary view를 API DTO로 변환한다. */
    private AnalysisResultSummaryResponse mapSummary(
        buaa.msasca.sca.core.port.out.persistence.UnifiedResultQueryPort.AnalysisResultSummaryView summary
    ) {
        return new AnalysisResultSummaryResponse(
            summary.analysisRunId(),
            new AnalysisResultSummaryResponse.Totals(
                summary.totalCount(),
                summary.codeqlOnlyCount(),
                summary.mscanOnlyCount(),
                summary.matchedCount()
            ),
            summary.severityBuckets().stream()
                .map(v -> new AnalysisResultSummaryResponse.SeverityBucket(v.severity(), v.count()))
                .toList(),
            summary.vulnerabilityTypeBuckets().stream()
                .map(v -> new AnalysisResultSummaryResponse.VulnerabilityTypeBucket(v.vulnerabilityType(), v.count()))
                .toList(),
            summary.moduleBuckets().stream()
                .map(v -> new AnalysisResultSummaryResponse.ModuleBucket(v.moduleName(), v.count()))
                .toList(),
            summary.flowEdges().stream()
                .map(v -> new AnalysisResultSummaryResponse.FlowEdge(v.sourceModule(), v.sinkModule(), v.count(), v.maxSeverity()))
                .toList(),
            summary.mscanFlowEdges().stream()
                .map(v -> new AnalysisResultSummaryResponse.FlowEdge(v.sourceModule(), v.sinkModule(), v.count(), v.maxSeverity()))
                .toList(),
            summary.codeqlFlowEdges().stream()
                .map(v -> new AnalysisResultSummaryResponse.FlowEdge(v.sourceModule(), v.sinkModule(), v.count(), v.maxSeverity()))
                .toList(),
            summary.findings().stream()
                .map(v -> new AnalysisResultSummaryResponse.Finding(
                    v.unifiedRecordId(),
                    v.severity(),
                    v.vulnerabilityType(),
                    v.title(),
                    v.sourceFilePath(),
                    v.sourceLine(),
                    v.sourceMethod(),
                    v.sinkFilePath(),
                    v.sinkLine(),
                    v.sinkMethod(),
                    v.sourceModule(),
                    v.sinkModule(),
                    v.scopeServiceModuleId(),
                    v.scopeServiceModuleName()
                ))
                .toList()
        );
    }

    @GetMapping("/analysis-runs/{analysisRunId}/stages")
    public AnalysisRunStagesResponse getStages(@PathVariable Long analysisRunId) {
        var v = runStagesUseCase.getStages(analysisRunId);
        return new AnalysisRunStagesResponse(
            v.analysisRunId(),
            v.analysisRunStatus(),
            new AnalysisRunStagesResponse.Stage(v.agentPrefilter().status(), v.agentPrefilter().latestToolRunId()),
            new AnalysisRunStagesResponse.Stage(v.build().status(), v.build().latestToolRunId()),
            new AnalysisRunStagesResponse.Stage(v.codeql().status(), v.codeql().latestToolRunId()),
            new AnalysisRunStagesResponse.Stage(v.agentKnowledge().status(), v.agentKnowledge().latestToolRunId()),
            new AnalysisRunStagesResponse.Stage(v.mscan().status(), v.mscan().latestToolRunId()),
            new AnalysisRunStagesResponse.Stage(v.merge().status(), v.merge().latestToolRunId()),
            v.currentEngine()
        );
    }
}