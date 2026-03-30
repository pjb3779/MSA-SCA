package buaa.msasca.sca.core.application.pipeline;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.mockito.Mockito;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import buaa.msasca.sca.core.application.service.CodeqlSarifIngestService;
import buaa.msasca.sca.core.application.service.UnifiedTaintMergeService;
import buaa.msasca.sca.core.domain.enums.BuildTool;
import buaa.msasca.sca.core.domain.enums.RunStatus;
import buaa.msasca.sca.core.domain.enums.ToolType;
import buaa.msasca.sca.core.domain.model.AnalysisRun;
import buaa.msasca.sca.core.domain.model.ProjectVersionSourceCache;
import buaa.msasca.sca.core.domain.model.ServiceModule;
import buaa.msasca.sca.core.domain.model.ToolRun;
import buaa.msasca.sca.core.port.out.persistence.AnalysisArtifactPort;
import buaa.msasca.sca.core.port.out.persistence.AnalysisRunCommandPort;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionSourceCachePort;
import buaa.msasca.sca.core.port.out.persistence.MscanGatewayYamlCommandPort;
import buaa.msasca.sca.core.port.out.persistence.MscanGatewayYamlPort;
import buaa.msasca.sca.core.port.out.persistence.MscanResultPort;
import buaa.msasca.sca.core.port.out.persistence.MscanRunSummaryCommandPort;
import buaa.msasca.sca.core.port.out.persistence.ServiceModuleCommandPort;
import buaa.msasca.sca.core.port.out.persistence.ServiceModulePort;
import buaa.msasca.sca.core.port.out.persistence.SanitizerResultCommandPort;
import buaa.msasca.sca.core.port.out.persistence.ToolRunCommandPort;
import buaa.msasca.sca.core.port.out.persistence.ToolRunPort;
import buaa.msasca.sca.core.port.out.tool.AgentPort;
import buaa.msasca.sca.core.port.out.tool.BuildImageResolver;
import buaa.msasca.sca.core.port.out.tool.BuildPort;
import buaa.msasca.sca.core.port.out.tool.CodeqlConfig;
import buaa.msasca.sca.core.port.out.tool.CodeqlPort;
import buaa.msasca.sca.core.port.out.tool.DockerImagePort;
import buaa.msasca.sca.core.port.out.tool.MscanPort;
import buaa.msasca.sca.core.port.out.tool.ServiceModuleScannerPort;
import buaa.msasca.sca.core.port.out.tool.StoragePort;
import buaa.msasca.sca.core.port.out.tool.ToolImageConfig;
import buaa.msasca.sca.core.port.out.tool.ToolLlmConfig;

public final class PipelineExecutorTestFactory {

  private PipelineExecutorTestFactory() {}

  public enum Mode {
    MSCAN_ONLY,
    NORMAL
  }

  public static final long MODULE_ID = 101L;
  public static final long PROJECT_VERSION_ID = 10L;

  public static final long AGENT_PREFILTER_TOOL_RUN_ID = 300L;
  public static final long BUILD_TOOL_RUN_ID = 200L;
  public static final long CODEQL_TOOL_RUN_ID = 210L;
  public static final long AGENT_KNOWLEDGE_TOOL_RUN_ID = 301L;
  public static final long MSCAN_TOOL_RUN_ID = 400L;

  public record Context(
      PipelineExecutor executor,
      ToolRunCommandPort toolRunCommandPort,
      CodeqlPort codeqlPort,
      AgentPort agentPort,
      MscanPort mscanPort,
      UnifiedTaintMergeService unifiedTaintMergeService,
      MscanResultPort mscanResultPort,
      MscanRunSummaryCommandPort mscanRunSummaryCommandPort,
      MscanGatewayYamlPort mscanGatewayYamlPort,
      MscanGatewayYamlCommandPort mscanGatewayYamlCommandPort,
      long analysisRunId
  ) {}

  public static Context create(
      Path tempDir,
      Mode mode,
      long analysisRunId,
      String mscanName,
      boolean agentPrefilterThrows,
      boolean agentKnowledgeThrows,
      boolean createJavaSourcesForNormal
  ) throws Exception {

    ObjectMapper om = new ObjectMapper();

    // ------------------------------------------------------------
    // 1) tempDir에 pipeline executor가 읽는 파일 배치
    // ------------------------------------------------------------
    Path gatewayPath = tempDir.resolve(".msasca/mscan/gateway.yml");
    Files.createDirectories(gatewayPath.getParent());
    Files.writeString(gatewayPath, "gateway: true\n", StandardCharsets.UTF_8);

    Path reportPath = tempDir.resolve(".msasca/mscan/report.txt");
    Files.createDirectories(reportPath.getParent());
    Files.writeString(
        reportPath,
        "2 TaintFlow{<org.springframework.cloud.skipper.server.controller.PackageController: org.springframework.hateoas.EntityModel upload(org.springframework.cloud.skipper.domain.UploadRequest)>/0 -> <org.springframework.cloud.skipper.server.service.PackageService: org.springframework.cloud.skipper.domain.PackageMetadata upload(org.springframework.cloud.skipper.domain.UploadRequest)>[43@L227] invokestatic java.nio.file.Files.write($r28, $r29, $r30)/0 --- VUL_ID:FileWriteVulnerability}\n",
        StandardCharsets.UTF_8
    );

    // MSCAN_ONLY: initMscanJarDir를 타지 않으므로 jarDir에 직접 *.jar를 만들어 둠
    if (mode == Mode.MSCAN_ONLY) {
      Path jarDir = tempDir.resolve(".msasca/mscan/jars");
      Files.createDirectories(jarDir);
      Files.writeString(jarDir.resolve("a.jar"), "jar-bytes", StandardCharsets.UTF_8);
    }

    // NORMAL: build stage에서 jar staging이 채워지므로 build/libs/app.jar만 준비
    Path moduleDir = tempDir.resolve("sm1");
    Files.createDirectories(moduleDir);
    Path sinkJavaPath = moduleDir.resolve("src/main/java/org/springframework/cloud/skipper/server/service/PackageService.java");
    Files.createDirectories(sinkJavaPath.getParent());
    Files.writeString(sinkJavaPath, "package org.springframework.cloud.skipper.server.service;\nclass PackageService {}\n", StandardCharsets.UTF_8);
    if (mode == Mode.NORMAL) {
      Path moduleLibs = moduleDir.resolve("build/libs");
      Files.createDirectories(moduleLibs);
      Files.writeString(moduleLibs.resolve("app.jar"), "jar-bytes", StandardCharsets.UTF_8);

      if (createJavaSourcesForNormal) {
        Files.writeString(moduleDir.resolve("A.java"), "class A {}", StandardCharsets.UTF_8);
      }
    }

    // ------------------------------------------------------------
    // 2) run/configJson, 도메인 객체 구성
    // ------------------------------------------------------------
    ObjectNode runConfig = om.createObjectNode();
    ObjectNode pipeline = runConfig.putObject("pipeline");
    pipeline.put("mode", mode == Mode.MSCAN_ONLY ? "MSCAN_ONLY" : "NORMAL");

    ObjectNode mscan = runConfig.putObject("mscan");
    mscan.put("name", mscanName);
    mscan.put("classpathKeywords", "kw");
    mscan.put("reuse", false);

    AnalysisRun run = new AnalysisRun(
        analysisRunId,
        PROJECT_VERSION_ID,
        runConfig,
        RunStatus.PENDING,
        null,
        null,
        "test",
        Instant.now(),
        Instant.now()
    );

    ProjectVersionSourceCache cache = new ProjectVersionSourceCache(
        10L,
        PROJECT_VERSION_ID,
        tempDir.toString(),
        true,
        null
    );

    ServiceModule module = new ServiceModule(
        MODULE_ID,
        PROJECT_VERSION_ID,
        "sm1",
        "sm1",
        BuildTool.MAVEN,
        "17",
        true,
        false,
        null
    );

    // ------------------------------------------------------------
    // 3) 기본 mock/stub 세팅
    // ------------------------------------------------------------
    AnalysisRunCommandPort analysisRunCommandPort = Mockito.mock(AnalysisRunCommandPort.class);
    ServiceModulePort serviceModulePort = Mockito.mock(ServiceModulePort.class);
    ProjectVersionSourceCachePort sourceCachePort = Mockito.mock(ProjectVersionSourceCachePort.class);
    ToolRunCommandPort toolRunCommandPort = Mockito.mock(ToolRunCommandPort.class);
    ToolRunPort toolRunPort = Mockito.mock(ToolRunPort.class);
    AnalysisArtifactPort artifactPort = Mockito.mock(AnalysisArtifactPort.class);
    StoragePort storagePort = Mockito.mock(StoragePort.class);
    BuildPort buildPort = Mockito.mock(BuildPort.class);
    BuildImageResolver buildImageResolver = Mockito.mock(BuildImageResolver.class);
    DockerImagePort dockerImagePort = Mockito.mock(DockerImagePort.class);
    CodeqlPort codeqlPort = Mockito.mock(CodeqlPort.class);
    CodeqlConfig codeqlConfig = Mockito.mock(CodeqlConfig.class);
    AgentPort agentPort = Mockito.mock(AgentPort.class);
    MscanPort mscanPort = Mockito.mock(MscanPort.class);
    ServiceModuleScannerPort serviceModuleScannerPort = Mockito.mock(ServiceModuleScannerPort.class);
    ServiceModuleCommandPort serviceModuleCommandPort = Mockito.mock(ServiceModuleCommandPort.class);
    ToolImageConfig toolImageConfig = Mockito.mock(ToolImageConfig.class);
    CodeqlSarifIngestService codeqlSarifIngestService = Mockito.mock(CodeqlSarifIngestService.class);
    MscanGatewayYamlPort mscanGatewayYamlPort = Mockito.mock(MscanGatewayYamlPort.class);
    MscanGatewayYamlCommandPort mscanGatewayYamlCommandPort = Mockito.mock(MscanGatewayYamlCommandPort.class);
    MscanRunSummaryCommandPort mscanRunSummaryCommandPort = Mockito.mock(MscanRunSummaryCommandPort.class);
    MscanResultPort mscanResultPort = Mockito.mock(MscanResultPort.class);
    SanitizerResultCommandPort sanitizerResultCommandPort = Mockito.mock(SanitizerResultCommandPort.class);
    UnifiedTaintMergeService unifiedTaintMergeService = Mockito.mock(UnifiedTaintMergeService.class);
    ToolLlmConfig toolLlmConfig = Mockito.mock(ToolLlmConfig.class);

    // (1) run / cache 조회
    Mockito.when(analysisRunCommandPort.findById(analysisRunId)).thenReturn(Optional.of(run));
    Mockito.when(sourceCachePort.findValidByProjectVersionId(PROJECT_VERSION_ID))
        .thenReturn(Optional.of(cache));
    Mockito.when(serviceModulePort.findByProjectVersionId(PROJECT_VERSION_ID)).thenReturn(List.of(module));

    // (2) source cache로부터 모듈 스캔(테스트용 1개 모듈만)
    ServiceModuleScannerPort.DetectedServiceModule detected =
        new ServiceModuleScannerPort.DetectedServiceModule(
            "sm1",
            "sm1",
            BuildTool.MAVEN,
            "17",
            true
        );
    Mockito.when(serviceModuleScannerPort.scan(eq(tempDir.toString()), any(Integer.class)))
        .thenReturn(List.of(detected));

    // (3) storagePort.put -> file:/// uri 반환
    Mockito.when(storagePort.put(anyString(), any(InputStream.class)))
        .thenAnswer(inv -> {
          String key = inv.getArgument(0, String.class);
          String uri = "file:///" + key.replace("\\", "/");
          return new StoragePort.StoredObject(key, uri);
        });

    // (4) Docker image ensure
    Mockito.when(dockerImagePort.ensurePresent(anyString(), Mockito.any(Duration.class)))
        .thenReturn(new DockerImagePort.EnsureResult(true, "stdout", "stderr"));

    // (5) MScan tool 실행 결과
    Mockito.when(mscanPort.runGlobalAnalysis(any(MscanPort.RunRequest.class)))
        .thenReturn(new MscanPort.RunResult(reportPath.toString(), "mscan-stdout", "mscan-stderr"));

    // (6) mscan/tool 설정 (docker image/llm key 필수)
    Mockito.when(toolImageConfig.mscanImage()).thenReturn("mscan-image");
    Mockito.when(toolLlmConfig.openAiApiKey()).thenReturn("test-key");
    Mockito.when(toolLlmConfig.openAiBaseUrl()).thenReturn("http://example");
    Mockito.when(toolLlmConfig.openAiModel()).thenReturn("model");

    // (7) build 관련(normal 모드에서만 사용)
    Mockito.when(buildImageResolver.resolve(eq(module), any(Path.class))).thenReturn("build-image-1");
    Mockito.when(buildPort.build(any(BuildPort.BuildRequest.class))).thenReturn(
        new BuildPort.BuildResult(
            0,
            "build-stdout",
            "build-stderr",
            "build-image-1",
            null
        )
    );

    // (8) agent prefilter stage
    if (agentPrefilterThrows) {
      Mockito.when(agentPort.prefilterDecisions(
              anyLong(),
              eq(PROJECT_VERSION_ID),
              eq(tempDir.toString()),
              anyList()
          ))
          .thenThrow(new RuntimeException("agent-prefilter-failed"));
    } else {
      List<AgentPort.PrefilterDecision> decisions = List.of(
          new AgentPort.PrefilterDecision(MODULE_ID, true, "ok")
      );
      Mockito.when(agentPort.prefilterDecisions(
              anyLong(),
              eq(PROJECT_VERSION_ID),
              eq(tempDir.toString()),
              anyList()
          ))
          .thenReturn(decisions);

      Mockito.when(agentPort.prefilterModules(
              anyLong(),
              eq(PROJECT_VERSION_ID),
              eq(tempDir.toString()),
              anyList()
          ))
          .thenReturn(List.of(module));
    }

    // (9) agent knowledge stage
    if (agentKnowledgeThrows) {
      Mockito.when(agentPort.buildKnowledge(
              anyLong(),
              eq(PROJECT_VERSION_ID),
              eq(analysisRunId),
              eq(tempDir.toString()),
              anyString()
          ))
          .thenThrow(new RuntimeException("agent-knowledge-failed"));
    } else {
      AgentPort.AgentKnowledge knowledge = new AgentPort.AgentKnowledge(
          "sanitizer-registry",
          "gateway-entries",
          null,
          "knowledge-built",
          null
      );
      Mockito.when(agentPort.buildKnowledge(
              anyLong(),
              eq(PROJECT_VERSION_ID),
              eq(analysisRunId),
              eq(tempDir.toString()),
              anyString()
          ))
          .thenReturn(knowledge);
    }

    // (10) toolRunCommandPort: tool_run 생성 리턴 고정
    ToolRun prefilterToolRun = new ToolRun(
        AGENT_PREFILTER_TOOL_RUN_ID,
        analysisRunId,
        ToolType.AGENT,
        "agent-prefilter",
        null,
        RunStatus.PENDING,
        null,
        null,
        null,
        Instant.now(),
        Instant.now()
    );
    ToolRun knowledgeToolRun = new ToolRun(
        AGENT_KNOWLEDGE_TOOL_RUN_ID,
        analysisRunId,
        ToolType.AGENT,
        "agent-knowledge",
        null,
        RunStatus.PENDING,
        null,
        null,
        null,
        Instant.now(),
        Instant.now()
    );
    ToolRun buildToolRun = new ToolRun(
        BUILD_TOOL_RUN_ID,
        analysisRunId,
        ToolType.BUILD,
        "docker-build",
        null,
        RunStatus.PENDING,
        null,
        null,
        null,
        Instant.now(),
        Instant.now()
    );
    ToolRun codeqlToolRun = new ToolRun(
        CODEQL_TOOL_RUN_ID,
        analysisRunId,
        ToolType.CODEQL,
        "codeql",
        null,
        RunStatus.PENDING,
        null,
        null,
        null,
        Instant.now(),
        Instant.now()
    );
    ToolRun mscanToolRun = new ToolRun(
        MSCAN_TOOL_RUN_ID,
        analysisRunId,
        ToolType.MSCAN,
        "mscan",
        null,
        RunStatus.PENDING,
        null,
        null,
        null,
        Instant.now(),
        Instant.now()
    );

    Mockito.when(toolRunCommandPort.createAgentRun(eq(analysisRunId), eq("agent-prefilter"), eq("agent"), any(JsonNode.class)))
        .thenReturn(prefilterToolRun);
    Mockito.when(toolRunCommandPort.createAgentRun(eq(analysisRunId), eq("agent-knowledge"), eq("agent"), any(JsonNode.class)))
        .thenReturn(knowledgeToolRun);
    Mockito.when(toolRunCommandPort.createBuildRun(eq(analysisRunId), eq(MODULE_ID), eq("docker-build"), any(JsonNode.class)))
        .thenReturn(buildToolRun);
    Mockito.when(toolRunCommandPort.createCodeqlRun(eq(analysisRunId), eq(MODULE_ID), eq("codeql"), any(JsonNode.class)))
        .thenReturn(codeqlToolRun);
    Mockito.when(toolRunCommandPort.createMscanRun(eq(analysisRunId), eq("mscan"), any(JsonNode.class)))
        .thenReturn(mscanToolRun);

    // ------------------------------------------------------------
    // 4) PipelineExecutor 생성
    // ------------------------------------------------------------
    PipelineExecutor executor = new PipelineExecutor(
        analysisRunCommandPort,
        serviceModulePort,
        sourceCachePort,
        toolRunCommandPort,
        toolRunPort,
        artifactPort,
        storagePort,
        buildPort,
        buildImageResolver,
        dockerImagePort,
        codeqlPort,
        codeqlConfig,
        agentPort,
        mscanPort,
        serviceModuleScannerPort,
        serviceModuleCommandPort,
        toolImageConfig,
        codeqlSarifIngestService,
        mscanGatewayYamlPort,
        mscanGatewayYamlCommandPort,
        mscanRunSummaryCommandPort,
        mscanResultPort,
        sanitizerResultCommandPort,
        unifiedTaintMergeService,
        toolLlmConfig
    );

    return new Context(
        executor,
        toolRunCommandPort,
        codeqlPort,
        agentPort,
        mscanPort,
        unifiedTaintMergeService,
        mscanResultPort,
        mscanRunSummaryCommandPort,
        mscanGatewayYamlPort,
        mscanGatewayYamlCommandPort,
        analysisRunId
    );
  }

  public static Context createMscanOnly(
      Path tempDir,
      long analysisRunId,
      String mscanName,
      boolean agentPrefilterThrows,
      boolean agentKnowledgeThrows
  ) throws Exception {
    return create(tempDir, Mode.MSCAN_ONLY, analysisRunId, mscanName, agentPrefilterThrows, agentKnowledgeThrows, false);
  }

  public static Context createNormal(
      Path tempDir,
      long analysisRunId,
      String mscanName,
      boolean agentPrefilterThrows,
      boolean agentKnowledgeThrows
  ) throws Exception {
    return create(tempDir, Mode.NORMAL, analysisRunId, mscanName, agentPrefilterThrows, agentKnowledgeThrows, false);
  }
}

