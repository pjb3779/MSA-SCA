package buaa.msasca.sca.core.application.pipeline;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import buaa.msasca.sca.core.domain.enums.ArtifactType;
import buaa.msasca.sca.core.domain.enums.RunStatus;
import buaa.msasca.sca.core.domain.model.AnalysisRun;
import buaa.msasca.sca.core.domain.model.ProjectVersionSourceCache;
import buaa.msasca.sca.core.domain.model.ServiceModule;
import buaa.msasca.sca.core.domain.model.ToolRun;
import buaa.msasca.sca.core.port.out.persistence.AnalysisArtifactPort;
import buaa.msasca.sca.core.port.out.persistence.AnalysisRunPort;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionSourceCachePort;
import buaa.msasca.sca.core.port.out.persistence.ServiceModulePort;
import buaa.msasca.sca.core.port.out.persistence.ToolRunCommandPort;
import buaa.msasca.sca.core.port.out.persistence.ToolRunPort;
import buaa.msasca.sca.core.port.out.tool.AgentPort;
import buaa.msasca.sca.core.port.out.tool.CodeqlPort;
import buaa.msasca.sca.core.port.out.tool.MscanPort;
import buaa.msasca.sca.core.port.out.tool.RunnerPort;
import buaa.msasca.sca.core.port.out.tool.StoragePort;

public class PipelineExecutor {
    private final AnalysisRunPort analysisRunPort;
    private final ServiceModulePort serviceModulePort;
    private final ProjectVersionSourceCachePort sourceCachePort;

    private final ToolRunCommandPort toolRunCommandPort;
    private final ToolRunPort toolRunPort;
    private final AnalysisArtifactPort artifactPort;

    private final RunnerPort runnerPort;
    private final StoragePort storagePort;

    private final CodeqlPort codeqlPort;
    private final AgentPort agentPort;
    private final MscanPort mscanPort;

    private final ObjectMapper om = new ObjectMapper();

    public PipelineExecutor(
        AnalysisRunPort analysisRunPort,
        ServiceModulePort serviceModulePort,
        ProjectVersionSourceCachePort sourceCachePort,
        ToolRunCommandPort toolRunCommandPort,
        ToolRunPort toolRunPort,
        AnalysisArtifactPort artifactPort,
        RunnerPort runnerPort,
        StoragePort storagePort,
        CodeqlPort codeqlPort,
        AgentPort agentPort,
        MscanPort mscanPort
    ) {
        this.analysisRunPort = analysisRunPort;
        this.serviceModulePort = serviceModulePort;
        this.sourceCachePort = sourceCachePort;
        this.toolRunCommandPort = toolRunCommandPort;
        this.toolRunPort = toolRunPort;
        this.artifactPort = artifactPort;
        this.runnerPort = runnerPort;
        this.storagePort = storagePort;
        this.codeqlPort = codeqlPort;
        this.agentPort = agentPort;
        this.mscanPort = mscanPort;
    }

    /**
     * 하나의 analysis_run을 end-to-end로 실행한다.
     * 실행 흐름:
     * 1. analysis_run 조회 및 상태 검증 (PENDING만 실행)
     * 2. project_version 기준 유효한 source cache 조회
     * 3. service_module 목록 조회
     * 4. analysis_run 상태를 RUNNING으로 전이
     * 5. BUILD → CODEQL → AGENT → MSCAN 순서로 tool_run 실행
     * 6. 모든 단계 성공 시 analysis_run을 DONE으로 종료
     * @param analysisRunId 실행할 analysis_run의 ID
     * @throws IllegalArgumentException analysis_run이 존재하지 않는 경우
     * @throws IllegalStateException source cache가 준비되지 않은 경우
     */
    public void execute(Long analysisRunId) {
        AnalysisRun run = analysisRunPort.findById(analysisRunId)
            .orElseThrow(() -> new IllegalArgumentException("analysis_run not found: " + analysisRunId));

        if (run.status() != RunStatus.PENDING) {
        return; // idempotent-ish
        }

        ProjectVersionSourceCache cache = sourceCachePort.findValidByProjectVersionId(run.projectVersionId())
            .orElseThrow(() -> new IllegalStateException("No valid source cache for projectVersionId=" + run.projectVersionId()));

        String sourcePath = cache.storagePath();
        List<ServiceModule> modules = serviceModulePort.findByProjectVersionId(run.projectVersionId());

        analysisRunPort.markRunning(analysisRunId);

        try {
        // 1) BUILD (per service module)
        for (ServiceModule m : modules) {
            ToolRun tr = toolRunCommandPort.createBuildRun(analysisRunId, m.id(), "local-runner", buildConfigJson(m));
            runTool(tr.id(), () -> buildModule(tr.id(), m, sourcePath));
        }

        // 2) CODEQL (per service module)
        for (ServiceModule m : modules) {
            ToolRun tr = toolRunCommandPort.createCodeqlRun(analysisRunId, m.id(), "codeql", codeqlConfigJson(m));
            runTool(tr.id(), () -> {
            codeqlPort.createDatabase(tr.id(), m.id(), sourcePath);
            codeqlPort.runQueries(tr.id(), m.id(), sourcePath);
            // 산출물 기록(실제 구현에서는 codeql db / sarif 경로를 넣으면 됨)
            storeTextArtifact(tr.id(), ArtifactType.CODEQL_DB, "codeql-db.txt", "codeql db placeholder");
            });
        }

        // 3) AGENT (once)
        {
            ToolRun tr = toolRunCommandPort.createAgentRun(analysisRunId, "gpt-model-placeholder", "agent", agentConfigJson());
            runTool(tr.id(), () -> {
            agentPort.buildSanitizerRegistry(tr.id(), run.projectVersionId(), sourcePath);
            storeTextArtifact(tr.id(), ArtifactType.AGENT_OUTPUT, "agent-output.txt", "agent output placeholder");
            });
        }

        // 4) MSCAN (once)
        {
            ToolRun tr = toolRunCommandPort.createMscanRun(analysisRunId, "mscan", mscanConfigJson());
            runTool(tr.id(), () -> {
            mscanPort.runGlobalAnalysis(tr.id(), run.projectVersionId(), sourcePath);
            storeTextArtifact(tr.id(), ArtifactType.MSCAN_REPORT, "mscan-report.txt", "mscan report placeholder");
            });
        }

        analysisRunPort.markDone(analysisRunId);
        } catch (Exception e) {
        analysisRunPort.markFailed(analysisRunId);
        throw e;
        }
    }

    /**
     * tool_run 실행에 대한 공통 래퍼 메서드
     * tool_run의 상태 전이를 일관되게 관리
     * 처리 규칙:
     * - 실행 전: tool_run 상태를 RUNNING으로 변경
     * - 정상 종료: tool_run 상태를 DONE으로 변경
     * - 예외 발생: tool_run 상태를 FAILED로 변경하고 에러 메시지 기록
     * tool_run 실패는 analysis_run 전체 실패로 간주
     * @param toolRunId 실행 대상 tool_run ID
     * @param body 실제 실행할 로직 (람다)
     */
    private void runTool(Long toolRunId, Runnable body) {
        toolRunPort.markRunning(toolRunId);
        try {
        body.run();
        toolRunPort.markDone(toolRunId);
        } catch (Exception e) {
        String msg = (e.getMessage() == null) ? e.toString() : e.getMessage();
        toolRunPort.markFailed(toolRunId, msg);
        // tool 실패면 전체 파이프라인도 실패 처리(현재 정책)
        throw e;
        }
    }

    /**
     * 하나의 서비스 모듈에 대해 빌드를 수행
     * 서비스 모듈의 buildTool 설정에 따라 Maven, Gradle, Jar(빌드 생략)커맨드를 선택하여 실행
     * 빌드 과정에서 발생한 stdout/stderr는 analysis_artifact로 저장
     * @param toolRunId BUILD tool_run ID
     * @param module 빌드 대상 서비스 모듈
     * @param sourcePath project_version 기준 소스 루트 경로
     * @throws IllegalStateException 빌드 커맨드가 실패(exitCode != 0)한 경우
     */
    private void buildModule(Long toolRunId, ServiceModule module, String sourcePath) {
        String workDir = normalizeDir(sourcePath, module.rootPath());

        List<String> cmd = switch (module.buildTool()) {
        case MAVEN -> List.of("mvn", "-DskipTests", "package");
        case GRADLE -> List.of("./gradlew", "build", "-x", "test");
        case JAR -> List.of("bash", "-lc", "echo 'BuildTool=JAR: skip build'");
        default -> List.of("bash", "-lc", "echo 'BuildTool=OTHER: skip build'");
        };

        RunnerPort.ExecResult res = runnerPort.run(new RunnerPort.ExecSpec(
            cmd,
            Map.of(),
            workDir,
            Duration.ofMinutes(30)
        ));

        // build log artifact 저장
        storeTextArtifact(toolRunId, ArtifactType.OTHER, "build-stdout.log", res.stdout());
        storeTextArtifact(toolRunId, ArtifactType.OTHER, "build-stderr.log", res.stderr());

        if (res.exitCode() != 0) {
        throw new IllegalStateException("Build failed: exitCode=" + res.exitCode());
        }
    }

     /**
     * 산출물을 스토리지에 저장하고,해당 결과를 analysis_artifact로 기록
     * 주로 로그(stdout/stderr)나, placeholder 결과물을 저장
     * @param toolRunId 산출물이 귀속될 tool_run ID
     * @param type 아티팩트 타입 (LOG, CODEQL_DB, MSCAN_REPORT 등)
     * @param filename 저장될 파일명
     * @param content 저장할 문자열 내용
     */
    private void storeTextArtifact(Long toolRunId, ArtifactType type, String filename, String content) {
        String key = "runs/toolRun/" + toolRunId + "/" + filename;
        var stored = storagePort.put(key, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));

        ObjectNode meta = om.createObjectNode();
        meta.put("filename", filename);
        meta.put("length", content.length());

        artifactPort.create(toolRunId, type, stored.uri(), meta);
    }

    /**
     * BUILD 단계에서 사용할 tool_run용 config_json을 생성
     * config_json에는 다음 정보가 포함
     * - serviceModuleId
     * - buildTool 종류
     * - 모듈의 rootPath
     * @param m 대상 서비스 모듈
     * @return BUILD 단계용 JSON 설정 객체
     */
    private ObjectNode buildConfigJson(ServiceModule m) {
        ObjectNode n = om.createObjectNode();
        n.put("serviceModuleId", m.id());
        n.put("buildTool", m.buildTool().name());
        n.put("rootPath", m.rootPath());
        return n;
    }

    /**
     * CODEQL에tj 사용할 config_json을 생성
     * 서비스 모듈 단위로 CodeQL 분석을 수행하기 위한 기본 실행 모드를 설정
     * @param m 대상 서비스 모듈
     * @return CODEQL 단계용 JSON 설정 객체
     */
    private ObjectNode codeqlConfigJson(ServiceModule m) {
        ObjectNode n = om.createObjectNode();
        n.put("serviceModuleId", m.id());
        n.put("mode", "default");
        return n;
    }

     /**
     * AGENT에서 사용할 config_json을 생성
     * 일단 sanitizer registry 생성용 단일 실행만 정의
     * @return AGENT 단계용 JSON 설정 객체
     */
    private ObjectNode agentConfigJson() {
        ObjectNode n = om.createObjectNode();
        n.put("mode", "sanitizer-registry");
        return n;
    }

    /**
     * MSCAN에서 사용할 config_json을 생성
     * 프로젝트 전체를 대상이므로 글로벌 분석 모드로 설정
     * @return MSCAN 단계용 JSON 설정 객체
     */
    private ObjectNode mscanConfigJson() {
        ObjectNode n = om.createObjectNode();
        n.put("mode", "global");
        return n;
    }

    /**
     * base 경로와 상대 경로를 결합하여 실제 작업 디렉토리 경로를 생성
     * 서비스 모듈의 rootPath가 없는 경우 project_version의 루트 디렉토리를 그대로 사용
     * @param base project_version 기준 소스 루트 경로
     * @param rel 서비스 모듈의 상대 경로
     * @return 결합된 작업 디렉토리 경로
     */
    private String normalizeDir(String base, String rel) {
        if (rel == null || rel.isBlank()) return base;
        if (base.endsWith("/")) return base + rel;
        return base + "/" + rel;
    }
}
