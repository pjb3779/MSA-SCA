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

    // -------------------------
    // Tool Execution Wrapper
    // -------------------------
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

    // -------------------------
    // BUILD internal logic
    // -------------------------
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

    // -------------------------
    // Artifact helpers
    // -------------------------
    private void storeTextArtifact(Long toolRunId, ArtifactType type, String filename, String content) {
        String key = "runs/toolRun/" + toolRunId + "/" + filename;
        var stored = storagePort.put(key, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));

        ObjectNode meta = om.createObjectNode();
        meta.put("filename", filename);
        meta.put("length", content.length());

        artifactPort.create(toolRunId, type, stored.uri(), meta);
    }

    // -------------------------
    // JSON config helpers
    // -------------------------
    private ObjectNode buildConfigJson(ServiceModule m) {
        ObjectNode n = om.createObjectNode();
        n.put("serviceModuleId", m.id());
        n.put("buildTool", m.buildTool().name());
        n.put("rootPath", m.rootPath());
        return n;
    }

    private ObjectNode codeqlConfigJson(ServiceModule m) {
        ObjectNode n = om.createObjectNode();
        n.put("serviceModuleId", m.id());
        n.put("mode", "default");
        return n;
    }

    private ObjectNode agentConfigJson() {
        ObjectNode n = om.createObjectNode();
        n.put("mode", "sanitizer-registry");
        return n;
    }

    private ObjectNode mscanConfigJson() {
        ObjectNode n = om.createObjectNode();
        n.put("mode", "global");
        return n;
    }

    private String normalizeDir(String base, String rel) {
        if (rel == null || rel.isBlank()) return base;
        if (base.endsWith("/")) return base + rel;
        return base + "/" + rel;
    }
}
