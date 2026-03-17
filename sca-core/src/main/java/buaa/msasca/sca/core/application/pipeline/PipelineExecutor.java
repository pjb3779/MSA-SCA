package buaa.msasca.sca.core.application.pipeline;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import buaa.msasca.sca.core.application.service.CodeqlSarifIngestService;
import buaa.msasca.sca.core.domain.enums.ArtifactType;
import buaa.msasca.sca.core.domain.enums.GatewayYamlProvidedBy;
import buaa.msasca.sca.core.domain.enums.GatewayYamlStatus;
import buaa.msasca.sca.core.domain.enums.MscanSummaryStatus;
import buaa.msasca.sca.core.domain.model.AnalysisRun;
import buaa.msasca.sca.core.domain.model.MscanGatewayYaml;
import buaa.msasca.sca.core.domain.model.ProjectVersionSourceCache;
import buaa.msasca.sca.core.domain.model.ServiceModule;
import buaa.msasca.sca.core.domain.model.ToolRun;
import buaa.msasca.sca.core.port.out.persistence.AnalysisArtifactPort;
import buaa.msasca.sca.core.port.out.persistence.AnalysisRunCommandPort;
import buaa.msasca.sca.core.port.out.persistence.MscanGatewayYamlCommandPort;
import buaa.msasca.sca.core.port.out.persistence.MscanGatewayYamlPort;
import buaa.msasca.sca.core.port.out.persistence.MscanResultPort;
import buaa.msasca.sca.core.port.out.persistence.MscanRunSummaryCommandPort;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionSourceCachePort;
import buaa.msasca.sca.core.port.out.persistence.ServiceModuleCommandPort;
import buaa.msasca.sca.core.port.out.persistence.ServiceModulePort;
import buaa.msasca.sca.core.port.out.persistence.ToolRunCommandPort;
import buaa.msasca.sca.core.port.out.persistence.ToolRunPort;
import buaa.msasca.sca.core.port.out.tool.AgentPort;
import buaa.msasca.sca.core.port.out.tool.BuildImageResolver;
import buaa.msasca.sca.core.port.out.tool.BuildPort;
import buaa.msasca.sca.core.port.out.tool.CodeqlPort;
import buaa.msasca.sca.core.port.out.tool.DockerImagePort;
import buaa.msasca.sca.core.port.out.tool.MscanPort;
import buaa.msasca.sca.core.port.out.tool.ServiceModuleScannerPort;
import buaa.msasca.sca.core.port.out.tool.StoragePort;
import buaa.msasca.sca.core.port.out.tool.ToolImageConfig;
import buaa.msasca.sca.core.port.out.tool.ToolLlmConfig;
import buaa.msasca.sca.core.application.error.InvalidConfigException;
import buaa.msasca.sca.core.application.error.ToolExecutionException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PipelineExecutor {

  private final AnalysisRunCommandPort analysisRunCommandPort;
  private final ServiceModulePort serviceModulePort;
  private final ProjectVersionSourceCachePort sourceCachePort;

  private final ToolRunCommandPort toolRunCommandPort;
  private final ToolRunPort toolRunPort;
  private final AnalysisArtifactPort artifactPort;

  private final StoragePort storagePort;

  private final BuildPort buildPort;
  private final BuildImageResolver buildImageResolver;

  private final DockerImagePort dockerImagePort;

  private final AgentPort agentPort;

  private final ServiceModuleScannerPort serviceModuleScannerPort;
  private final ServiceModuleCommandPort serviceModuleCommandPort;

  private final CodeqlPort codeqlPort;
  private final CodeqlSarifIngestService codeqlSarifIngestService;

  private final MscanPort mscanPort;
  private final MscanGatewayYamlPort mscanGatewayYamlPort;
  private final MscanGatewayYamlCommandPort mscanGatewayYamlCommandPort;
  private final MscanRunSummaryCommandPort mscanRunSummaryCommandPort;
  private final MscanResultPort mscanResultPort;
  private final ToolLlmConfig toolLlmConfig;
  private final ToolImageConfig toolImageConfig;

  private final ObjectMapper om = new ObjectMapper();

  //gateway얌 파일 / jar 폴더 / report 파일(컨테이너 실행 계약)
  private static final String MSCAN_GATEWAY_REL_PATH = ".msasca/mscan/gateway.yml";
  private static final String MSCAN_JAR_DIR_REL_PATH = ".msasca/mscan/jars";
  private static final String MSCAN_REPORT_REL_PATH  = ".msasca/mscan/report.txt";
  
  public PipelineExecutor(
      AnalysisRunCommandPort analysisRunCommandPort,
      ServiceModulePort serviceModulePort,
      ProjectVersionSourceCachePort sourceCachePort,
      ToolRunCommandPort toolRunCommandPort,
      ToolRunPort toolRunPort,
      AnalysisArtifactPort artifactPort,
      StoragePort storagePort,
      BuildPort buildPort,
      BuildImageResolver buildImageResolver,
      DockerImagePort dockerImagePort,
      CodeqlPort codeqlPort,
      AgentPort agentPort,
      MscanPort mscanPort,
      ServiceModuleScannerPort serviceModuleScannerPort,
      ServiceModuleCommandPort serviceModuleCommandPort,
      ToolImageConfig toolImageConfig,
      CodeqlSarifIngestService codeqlSarifIngestService,
      MscanGatewayYamlPort mscanGatewayYamlPort,
      MscanGatewayYamlCommandPort mscanGatewayYamlCommandPort,
      MscanRunSummaryCommandPort mscanRunSummaryCommandPort,
      MscanResultPort mscanResultPort,
      ToolLlmConfig toolLlmConfig
  ) {
    this.analysisRunCommandPort = analysisRunCommandPort;
    this.serviceModulePort = serviceModulePort;
    this.sourceCachePort = sourceCachePort;
    this.toolRunCommandPort = toolRunCommandPort;
    this.toolRunPort = toolRunPort;
    this.artifactPort = artifactPort;
    this.storagePort = storagePort;
    this.buildPort = buildPort;
    this.buildImageResolver = buildImageResolver;
    this.dockerImagePort = dockerImagePort;
    this.codeqlPort = codeqlPort;
    this.agentPort = agentPort;
    this.mscanPort = mscanPort;
    this.serviceModuleScannerPort = serviceModuleScannerPort;
    this.serviceModuleCommandPort = serviceModuleCommandPort;
    this.toolImageConfig = toolImageConfig;
    this.codeqlSarifIngestService = codeqlSarifIngestService;
    this.mscanGatewayYamlPort = mscanGatewayYamlPort;
    this.mscanGatewayYamlCommandPort = mscanGatewayYamlCommandPort;
    this.mscanRunSummaryCommandPort = mscanRunSummaryCommandPort;
    this.mscanResultPort = mscanResultPort;
    this.toolLlmConfig = toolLlmConfig;
  }

  /**
   * 하나의 analysis_run을 end-to-end로 실행한다.
   * 실행 흐름:
   * 1) 입력 조회/검증(PENDING만 실행)
   * 2) source cache 조회
   * 3) service module 스캔 & upsert
   * 4) service module 조회
   * 5) RUNNING 전이
   * 6) 빌드 이미지 선-ensure(distinct)
   * 7) 단계 실행(BUILD→CODEQL→AGENT→MSCAN)
   * 8) 성공 시 DONE, 실패 시 FAILED
   *
   * // 상태 전이(markRunning/markDone/markFailed)는 RunPollingJob가 책임
   * @param analysisRunId 실행할 analysis_run ID
   */
  public void execute(Long analysisRunId) {
    AnalysisRun run = loadRunOrThrow(analysisRunId);
    if (run == null) return;

    ProjectVersionSourceCache cache = loadSourceCacheOrThrow(run.projectVersionId());
    String sourceRootPath = cache.storagePath();

    scanAndUpsertServiceModules(run.projectVersionId(), sourceRootPath, 6);

    List<ServiceModule> modules = serviceModulePort.findByProjectVersionId(run.projectVersionId());

    if (modules.isEmpty()) {
      throw new IllegalStateException("No service modules detected for projectVersionId=" + run.projectVersionId());
    }

    // 테스트용
    String mode = "";
    if (run.configJson() != null) {
      mode = run.configJson().path("pipeline").path("mode").asText("");
    }
    boolean mscanOnly = "MSCAN_ONLY".equalsIgnoreCase(mode);

    //mscan 얌파일 체크
    requireGatewayModuleAndYaml(run.projectVersionId(), sourceRootPath, modules);

    try {
      log.info("[PIPE] start analysisRunId={}, projectVersionId={}", analysisRunId, run.projectVersionId());

      if (mscanOnly) {
        runMscanStage(run, sourceRootPath, modules);
        return;
      }

      //모듈별 이미지 결정(파일 기반) + distinct 이미지 선-ensure
      List<BuildPlan> buildPlans = prepareBuildPlans(modules, sourceRootPath);

      //     buildPlans.size(),
      //     buildPlans.stream().map(BuildPlan::image).distinct().toList()
      // );
      ensureBuildImages(buildPlans, Duration.ofMinutes(20));

      /////////////////////////////////////////////////////////////////////////////////////
      /// (현재) 단계 순서: BUILD -> CODEQL -> AGENT -> MSCAN
      /// 사용자가 제시한 최종 순서로 바꾸려면 아래 호출 순서만 재배치하면 됨.
      /////////////////////////////////////////////////////////////////////////////////////

      //mscan관련 전처리
      //gateway.yml 요구 체크(게이트웨이 모듈이 있을 때만)
      initMscanJarDir(sourceRootPath);

      //log.info("[PIPE] step6 runBuildStage start");
      runBuildStage(analysisRunId, buildPlans, sourceRootPath);

      //runCodeqlStage(analysisRunId, modules, sourceRootPath);

      // runAgentStage(analysisRunId, run.projectVersionId(), sourceRootPath);

      //런 내부의 값을 반드시 읽어야함
      runMscanStage(run, sourceRootPath, modules);

      log.info("[PIPE] finished OK analysisRunId={}", analysisRunId);

    } catch (Exception e) {
      log.error("[PIPE] FAILED analysisRunId={} reason={}", analysisRunId, e.toString(), e);

      //RunPollingjob이 Mark fail 처리 
      throw e;
    }
  }

  /**
   * analysis_run을 조회한다.
   *
   * @param analysisRunId analysis_run ID
   * @return AnalysisRun
   */
  private AnalysisRun loadRunOrThrow(Long analysisRunId) {
    return analysisRunCommandPort.findById(analysisRunId)
        .orElseThrow(() -> new IllegalArgumentException("analysis_run not found: " + analysisRunId));
  }

  /**
   * project_version 기준 유효한 source cache를 조회한다.
   *
   * @param projectVersionId project_version ID
   * @return 유효한 source cache
   */
  private ProjectVersionSourceCache loadSourceCacheOrThrow(Long projectVersionId) {
    return sourceCachePort.findValidByProjectVersionId(projectVersionId)
        .orElseThrow(() -> new IllegalStateException("No valid source cache for projectVersionId=" + projectVersionId));
  }

  /**
   * tool_run 실행 공통 래퍼.
   *
   * - RUNNING 으로 전이
   * - body 실행
   * - 성공 시 DONE
   * - 실패 시 FAILED + error_message 기록
   * - 추가: 실패 시 예외 메시지를 artifact 파일(tool-error.log)로도 남김
   *
   * @param toolRunId tool_run ID
   * @param body 실행 로직
   */
  private void runTool(Long toolRunId, Runnable body) {
      toolRunPort.markRunning(toolRunId);
      try {
          body.run();
          toolRunPort.markDone(toolRunId);
      } catch (Exception e) {
          String msg = (e.getMessage() == null) ? e.toString() : e.getMessage();
          msg = sanitizeDbText(msg);
          
          String stack;
          try (var sw = new java.io.StringWriter(); var pw = new java.io.PrintWriter(sw)) {
              e.printStackTrace(pw);
              stack = sw.toString();
          } catch (Exception ignored) {
              stack = "[stacktrace unavailable]";
          }
          
          try {
              storeTextArtifact(
                  toolRunId,
                  ArtifactType.OTHER,
                  "tool-error.log",
                  msg
              );
          } catch (Exception logEx) {
              log.warn(
                  "[PIPE] failed to store tool-error.log for toolRunId={}",
                  toolRunId,
                  logEx
              );
          }

          toolRunPort.markFailed(toolRunId, msg);
          throw e;
      }
  }

  /**
   * PostgreSQL text 컬럼에 넣기 안전하게 문자열을 정리한다.
   * - 널 바이트(\u0000) 제거
   */
  private String sanitizeDbText(String s) {
    if (s == null) return null;
    // Postgres는 text/varchar에 \0 허용 안 함
    return s.replace("\u0000", "");
  }

  /**
   * 소스 루트를 스캔하여 service_module을 upsert한다.
   *
   * @param projectVersionId project_version ID
   * @param sourceRootPath 소스 루트 경로
   * @param maxDepth 탐색 깊이
   */
  private void scanAndUpsertServiceModules(Long projectVersionId, String sourceRootPath, int maxDepth) {
    List<ServiceModuleScannerPort.DetectedServiceModule> detected =
        serviceModuleScannerPort.scan(sourceRootPath, maxDepth);

    for (ServiceModuleScannerPort.DetectedServiceModule d : detected) {
      serviceModuleCommandPort.upsert(
          projectVersionId,
          d.name(),
          d.rootPath(),
          d.buildTool(),
          d.jdkVersion(),
          d.isGateway()
      );
    }
  }

  /////////////////////////////////////////////////////////////////////////////////////
  /// BUILD 준비(이미지 결정 + 선-ensure)
  /////////////////////////////////////////////////////////////////////////////////////

  /**
   * 모듈별 빌드 계획(이미지 포함)을 만든다.
   * 이미지 결정은 BuildImageResolver(module, moduleDir)로 수행한다.
   *
   * @param modules 서비스 모듈 목록
   * @param sourceRootPath 소스 루트 경로
   * @return 빌드 계획 목록
   */
  private List<BuildPlan> prepareBuildPlans(List<ServiceModule> modules, String sourceRootPath) {
    List<BuildPlan> plans = new ArrayList<>();
    for (ServiceModule m : modules) {
      Path moduleDir = Path.of(normalizeDir(sourceRootPath, m.rootPath()));
      String image = buildImageResolver.resolve(m, moduleDir);
      plans.add(new BuildPlan(m, moduleDir, image));
    }
    return List.copyOf(plans);
  }

  /**
   * 빌드 단계 시작 전에 필요한 Docker 이미지들을 distinct로 선-ensure 한다.
   *
   * @param plans 빌드 계획 목록
   * @param timeout ensure 타임아웃
   */
  private void ensureBuildImages(List<BuildPlan> plans, Duration timeout) {
    plans.stream()
        .map(BuildPlan::image)
        .distinct()
        .forEach(img -> {
          try {
            dockerImagePort.ensurePresent(img, timeout);
          } catch (Exception e) {
            throw new IllegalStateException("Docker image ensure failed: " + img, e);
          }
        });
  }

  /** BUILD 단계에서 사용할 내부 계획 레코드 */
  private record BuildPlan(ServiceModule module, Path moduleDir, String image) {}
  
  /////////////////////////////////////////////////////////////////////////////////////
  /// BUILD 메서드
  /////////////////////////////////////////////////////////////////////////////////////

  /**
   * BUILD 단계를 실행한다(서비스 모듈 단위).
   * - BuildPlan에서 이미지가 이미 확정된 상태
   * - tool_run 생성 시 config_json에 dockerImage 포함
   * - BuildPort로 Docker 빌드 수행
   * - 빌드 성공 시 JAR 자동 수집
   *
   * @param analysisRunId analysis_run ID
   * @param plans 빌드 계획 목록(이미지 포함)
   * @param sourceRootPath 소스 루트 경로
   */
  private void runBuildStage(Long analysisRunId, List<BuildPlan> plans, String sourceRootPath) {
    for (BuildPlan plan : plans) {
      ServiceModule m = plan.module();
      String image = plan.image();

      ToolRun tr = toolRunCommandPort.createBuildRun(
          analysisRunId,
          m.id(),
          "docker-build",
          buildConfigJson(m, image)
      );

      runTool(tr.id(), () -> buildModule(tr.id(), m, sourceRootPath, image));
    }
  }

  /**
   * 하나의 서비스 모듈을 빌드한다(Docker 기반).
   *
   * @param toolRunId build tool_run ID
   * @param module 서비스 모듈
   * @param sourceRootPath 소스 루트 경로(호스트)
   * @param dockerImage 빌드 컨테이너 이미지
   */
  private void buildModule(Long toolRunId, ServiceModule module, String sourceRootPath, String dockerImage) {
    var res = buildPort.build(new BuildPort.BuildRequest(
        toolRunId,
        module.id(),
        sourceRootPath,
        module.rootPath(),
        module.buildTool(),
        dockerImage,
        buildTimeoutFor(module)
    ));

    storeTextArtifact(toolRunId, ArtifactType.OTHER, "build-image.log", "image=" + res.dockerImageUsed());
    storeTextArtifact(toolRunId, ArtifactType.OTHER, "build-stdout.log", res.stdout());
    storeTextArtifact(toolRunId, ArtifactType.OTHER, "build-stderr.log", res.stderr());

    if (res.exitCode() != 0) {
      throw new IllegalStateException("Build failed: exitCode=" + res.exitCode());
    }

    String builtRoot = res.builtSourceRootPathOnHost();
    storeTextArtifact(toolRunId, ArtifactType.OTHER, "build-workroot.log",
        "builtSourceRootPathOnHost=" + builtRoot + ", originalSourceRootPath=" + sourceRootPath);

    String jarCollectRoot = (builtRoot == null || builtRoot.isBlank()) ? sourceRootPath : builtRoot;

    List<Path> builtJars = collectAndStoreJars(toolRunId, module, jarCollectRoot);

    stageMscanJars(sourceRootPath, module, builtJars);
  }

  private void stageMscanJars(String sourceRootPath, ServiceModule module, List<Path> jars) {
    try {
      if (jars == null || jars.isEmpty()) return;

      Path jarDir = Path.of(sourceRootPath).resolve(MSCAN_JAR_DIR_REL_PATH);
      Files.createDirectories(jarDir);

      for (Path jar : jars) {
        // 파일명 충돌 방지: serviceModuleId prefix
        String outName = "sm-" + module.id() + "-" + jar.getFileName().toString();
        Files.copy(jar, jarDir.resolve(outName), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (Exception e) {
      // staging 실패는 mscan 실패로 이어지므로 예외로 올리는 게 낫다
      throw new IllegalStateException("Failed to stage mscan jars for serviceModuleId=" + module.id(), e);
    }
  }

  private void collectMscanJarsFromStorage(String sourceRootPath, List<ServiceModule> modules) {
    try {
      Path jarDir = Path.of(sourceRootPath).resolve(MSCAN_JAR_DIR_REL_PATH);
      Files.createDirectories(jarDir);

      int copied = 0;

      // dev/local: storage의 runs/toolRun 아래에서 serviceModule-{id}/jars/*.jar 찾아 복사
      List<StoragePort.StoredObject> all = storagePort.list("runs/toolRun/");
      for (StoragePort.StoredObject obj : all) {
        String key = obj.key().replace("\\", "/");
        if (!key.endsWith(".jar")) continue;
        if (!key.contains("/jars/")) continue;

        boolean matchesAny = modules.stream()
            .anyMatch(m -> key.contains("serviceModule-" + m.id() + "/jars/"));
        if (!matchesAny) continue;

        String filename = Path.of(key).getFileName().toString();

        // 충돌 방지: key를 일부 포함
        String safeKey = key.replace("/", "_");
        if (safeKey.length() > 140) safeKey = safeKey.substring(safeKey.length() - 140);

        Path out = jarDir.resolve(safeKey + "-" + filename);

        try (InputStream in = storagePort.open(obj.uri())) {
          Files.copy(in, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        copied++;
      }

      log.info("[PIPE] collectedFromStorage={}, jarDir={}", copied, jarDir);

    } catch (Exception e) {
      throw new IllegalStateException("Failed to collect mscan jars from storage", e);
    }
  }
  // toolRunId가 없을 때도 storeTextArtifact를 쓰고 싶으면 별도 로깅 방식이 필요하지만,
  // 여기서는 간단히 analysisRunId를 대신 쓰지 말고 그냥 예외 메시지만 올리거나,
  // 별도 logger로 충분합니다. (storeTextArtifact는 toolRunId 필수라면 제거)
  private Long nullSafeToolRunIdForLog(Long analysisRunId) {
    return 0L; // 필요하면 toolRunId 기반으로만 로깅하도록 수정
  }

  /**
   * 빌드 타임아웃을 서비스 모듈 특성에 따라 설정한다.
   *
   * @param module 서비스 모듈
   * @return 타임아웃
   */
  private Duration buildTimeoutFor(ServiceModule module) {
    return switch (module.buildTool()) {
      case GRADLE -> Duration.ofMinutes(45);
      case MAVEN -> Duration.ofMinutes(30);
      default -> Duration.ofMinutes(20);
    };
  }

  /////////////////////////////////////////////////////////////////////////////////////
  /// Jar 수집 메서드
  /////////////////////////////////////////////////////////////////////////////////////

  /**
   * 빌드 결과로 생성된 JAR 파일들을 자동 수집하여 storage에 업로드하고,
   * analysis_artifact(ArtifactType.JAR)로 기록한다.
   *
   * - Gradle: <module>/build/libs/*.jar (bootJar 우선, plain은 후보로 두되 primary 우선순위 낮춤)
   * - Maven:  <module>/target/*.jar (original-*.jar 제외)
   *
   * fallback:
   * - moduleDir 전체 walk 금지
   * - build/libs, target 아래만 제한 깊이로 탐색
   *
   * @param toolRunId build tool_run ID (JAR artifact는 build tool_run에 귀속)
   * @param module 서비스 모듈
   * @param sourceRootPath 소스 루트 경로
   */
  private List<Path> collectAndStoreJars(Long toolRunId, ServiceModule module, String sourceRootPath) {
    Path moduleDir = Path.of(normalizeDir(sourceRootPath, module.rootPath()));
    
    //로그찍기
    storeTextArtifact(toolRunId, ArtifactType.OTHER, "jar-collect-root.log",
    "collectRoot=" + sourceRootPath + ", moduleRootPath=" + module.rootPath() + ", moduleDir=" + moduleDir);

    if (!Files.exists(moduleDir) || !Files.isDirectory(moduleDir)) {
      storeTextArtifact(toolRunId, ArtifactType.OTHER, "jar-collect.log",
          "Module dir not found: " + moduleDir);
      return List.of();
    }

    List<Path> candidates = findJarCandidates(moduleDir, module);
    if (candidates.isEmpty()) {
      storeTextArtifact(toolRunId, ArtifactType.OTHER, "jar-collect.log",
          "No jar found. moduleDir=" + moduleDir
              + ", buildTool=" + module.buildTool()
              + ", searched=[build/libs,target]");
      return List.of();
    }

    Optional<Path> primary = choosePrimaryJar(moduleDir, candidates);

    int uploaded = 0;
    for (Path jar : candidates) {
      boolean isPrimary = primary.isPresent() && primary.get().equals(jar);
      storeJarArtifact(toolRunId, module, moduleDir, jar, isPrimary);
      uploaded++;
    }

    storeTextArtifact(toolRunId, ArtifactType.OTHER, "jar-collect.log",
        "Collected jars=" + uploaded
            + ", primary=" + primary.map(p -> moduleDir.relativize(p).toString()).orElse("none")
            + ", moduleDir=" + moduleDir);

    // MScan staging에서 재사용할 “로컬 jar 경로 리스트” 반환
    return List.copyOf(candidates);
  }

  /**
   * 서비스 모듈 디렉토리에서 JAR 후보를 수집한다.
   *
   * 원칙:
   * - moduleDir 전체 walk 금지
   * - 산출 디렉토리(build/libs, target)만 탐색
   * - Gradle wrapper, sources/javadoc/tests/original 등 제외
   *
   * @param moduleDir 모듈 루트 디렉토리
   * @param module 서비스 모듈
   * @return JAR 후보 리스트(필터링 적용)
   */
  private List<Path> findJarCandidates(Path moduleDir, ServiceModule module) {
    List<Path> result = new ArrayList<>();

    Path gradleLibs = moduleDir.resolve("build").resolve("libs");
    Path mavenTarget = moduleDir.resolve("target");

    // 1) 우선: 산출 디렉토리 바로 아래
    result.addAll(listJarsInDir(gradleLibs));
    result.addAll(listJarsInDir(mavenTarget));

    // 2) fallback: 산출 디렉토리 하위만 제한 깊이로 탐색(모듈 전체는 금지)
    if (result.isEmpty()) {
      result.addAll(findJarsByWalk(gradleLibs, 3));
      result.addAll(findJarsByWalk(mavenTarget, 3));
    }

    // 3) 필터 + 중복 제거
    return result.stream()
        .filter(p -> isValidJarCandidate(moduleDir, p))
        .distinct()
        .toList();
  }

  /**
   * 특정 디렉토리 바로 아래의 *.jar 파일을 수집한다.
   *
   * @param dir 탐색 디렉토리
   * @return jar 파일 리스트
   */
  private List<Path> listJarsInDir(Path dir) {
    if (dir == null) return List.of();
    if (!Files.exists(dir) || !Files.isDirectory(dir)) return List.of();

    try (Stream<Path> s = Files.list(dir)) {
      return s.filter(Files::isRegularFile)
          .filter(p -> p.getFileName().toString().endsWith(".jar"))
          .toList();
    } catch (Exception e) {
      return List.of();
    }
  }

  /**
   * 특정 디렉토리를 제한 깊이로 walk 하며 *.jar를 수집한다.
   * (dir이 없으면 빈 리스트)
   *
   * @param dir 탐색 시작 디렉토리
   * @param maxDepth 최대 깊이
   * @return jar 파일 리스트
   */
  private List<Path> findJarsByWalk(Path dir, int maxDepth) {
    if (dir == null) return List.of();
    if (!Files.exists(dir) || !Files.isDirectory(dir)) return List.of();

    try (Stream<Path> s = Files.find(
        dir,
        maxDepth,
        (p, attr) -> attr.isRegularFile() && p.getFileName().toString().endsWith(".jar")
    )) {
      return s.toList();
    } catch (Exception e) {
      return List.of();
    }
  }

  /**
   * JAR 후보 필터링 규칙.
   *
   * 제외:
   * - gradle/wrapper/gradle-wrapper.jar
   * - sources/javadoc/tests
   * - Maven spring-boot plugin original-*.jar
   *
   * @param moduleDir 모듈 루트(상대경로 판단용)
   * @param jarPath jar 파일 경로
   * @return 후보로 인정하면 true
   */
  private boolean isValidJarCandidate(Path moduleDir, Path jarPath) {
    String name = jarPath.getFileName().toString();
    if (!name.endsWith(".jar")) return false;

    String rel = safeRelPath(moduleDir, jarPath);

    //Gradle wrapper 제외
    if (name.equals("gradle-wrapper.jar")) return false;
    if (rel.startsWith("gradle/wrapper/")) return false;

    // 빌드 산출물 경로 외에서 나온 jar는 배제 (안전장치)
    // 우리가 탐색을 build/libs, target로 제한했지만, 혹시 모를 누수 방지
    if (!(rel.startsWith("build/libs/") || rel.startsWith("target/"))) return false;

    // 흔한 보조 jar 제외
    if (name.endsWith("-sources.jar")) return false;
    if (name.endsWith("-javadoc.jar")) return false;
    if (name.endsWith("-tests.jar")) return false;
    if (name.endsWith("-test.jar")) return false;

    // Maven spring-boot plugin original 제외
    if (name.startsWith("original-")) return false;

    return true;
  }

  /**
   * jar 파일을 storage로 업로드하고 analysis_artifact로 기록한다.
   *
   * @param toolRunId tool_run ID
   * @param module 서비스 모듈
   * @param moduleDir 모듈 루트 디렉토리(상대경로 계산용)
   * @param jarPath jar 파일 경로
   * @param primary primary 여부
   */
  private void storeJarArtifact(Long toolRunId, ServiceModule module, Path moduleDir, Path jarPath, boolean primary) {
    try {
      String filename = jarPath.getFileName().toString();
      long size = Files.size(jarPath);
      String sha256 = computeSha256Hex(jarPath);

      // storage key: toolRun + module + jars
      String key = "runs/toolRun/" + toolRunId + "/serviceModule-" + module.id() + "/jars/" + filename;

      try (InputStream in = Files.newInputStream(jarPath)) {
        var stored = storagePort.put(key, in);

        ObjectNode meta = om.createObjectNode();
        meta.put("filename", filename);
        meta.put("size", size);
        meta.put("sha256", sha256);
        meta.put("primary", primary);

        meta.put("serviceModuleId", module.id());
        meta.put("projectVersionId", module.projectVersionId());

        // 모듈 기준 상대경로(디버깅/추적용)
        String rel = moduleDir.relativize(jarPath).toString().replace("\\", "/");
        meta.put("relativePath", rel);

        artifactPort.create(toolRunId, ArtifactType.JAR, stored.uri(), meta);
      }
    } catch (Exception e) {
      // 실패 로그를 artifact로 남김
      storeTextArtifact(toolRunId, ArtifactType.OTHER,
          "jar-collect-error-" + jarPath.getFileName() + ".log",
          "Failed to upload jar: " + jarPath + "\n" + e);
      throw new IllegalStateException("Failed to store jar artifact: " + jarPath, e);
    }
  }

  /**
   * 여러 후보 중 primary JAR 1개를 선정한다.
   *
   * 우선순위:
   * 1) -plain.jar가 아닌 것 우선(Gradle bootJar 우선)
   * 2) 크기 큰 것 우선
   * 3) 수정시간 최신 우선
   * 4) 파일명 사전순
   *
   * @param moduleDir 모듈 루트(상대경로 계산용)
   * @param candidates 후보 리스트
   * @return primary 후보
   */
  private Optional<Path> choosePrimaryJar(Path moduleDir, List<Path> candidates) {
    return candidates.stream()
        .max(Comparator
            .comparingInt((Path p) -> primaryRank(p))              // 높은 점수 우선
            .thenComparingLong(this::safeSize)                     // 큰 파일 우선
            .thenComparing(this::safeMtime)                        // 최신 우선
            .thenComparing(p -> safeRelPath(moduleDir, p))         // 안정적 tie-break
        );
  }

  /**
   * primary 선정용 랭크 점수를 계산한다.
   * -plain.jar는 점수를 낮춰서 primary에서 밀리게 한다.
   *
   * @param p jar 경로
   * @return 랭크 점수(클수록 우선)
   */
  private int primaryRank(Path p) {
    String name = p.getFileName().toString();

    // bootJar가 존재하면 보통 plain이 같이 생김 → plain은 낮은 우선순위
    if (name.endsWith("-plain.jar")) return 0;

    // 그 외 일반 jar는 우선
    return 1;
  }

  /**
   * moduleDir 기준 상대경로를 안전하게 계산한다.
   *
   * @param moduleDir 모듈 루트
   * @param p 대상 경로
   * @return 상대경로(슬래시 표준화)
   */
  private String safeRelPath(Path moduleDir, Path p) {
    try {
      return moduleDir.relativize(p).toString().replace("\\", "/");
    } catch (Exception e) {
      return p.toString().replace("\\", "/");
    }
  }

  /**
   * 파일 크기를 안전하게 가져온다(실패 시 0).
   *
   * @param p 파일 경로
   * @return 파일 크기
   */
  private long safeSize(Path p) {
    try {
      return Files.size(p);
    } catch (Exception e) {
      return 0L;
    }
  }

  /**
   * 파일 수정 시간을 안전하게 가져온다(실패 시 epoch).
   *
   * @param p 파일 경로
   * @return 수정 시간
   */
  private FileTime safeMtime(Path p) {
    try {
      return Files.getLastModifiedTime(p);
    } catch (Exception e) {
      return FileTime.fromMillis(0L);
    }
  }

  /**
   * 파일의 SHA-256 해시를 hex 문자열로 계산한다.
   *
   * @param path 파일 경로
   * @return sha256 hex
   */
  private String computeSha256Hex(Path path) {
    try (InputStream in = Files.newInputStream(path)) {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] buf = new byte[8192];
      int r;
      while ((r = in.read(buf)) >= 0) {
        if (r > 0) md.update(buf, 0, r);
      }
      return HexFormat.of().formatHex(md.digest());
    } catch (Exception e) {
      return "unknown";
    }
  }

  /////////////////////////////////////////////////////////////////////////////////////
  /// CODEQL 메서드
  /////////////////////////////////////////////////////////////////////////////////////

  /**
   * CODEQL 단계를 실행한다(서비스 모듈 단위).
   *
   * @param analysisRunId analysis_run ID
   * @param modules 서비스 모듈 목록
   * @param sourceRootPath 소스 루트 경로
   */
  private void runCodeqlStage(Long analysisRunId, List<ServiceModule> modules, String sourceRootPath) {
    for (ServiceModule m : modules) {
      ToolRun tr = toolRunCommandPort.createCodeqlRun(analysisRunId, m.id(), "codeql", codeqlConfigJson(m));

      runTool(tr.id(), () -> {
        String buildCmd = codeqlBuildCommandFor(m);

        CodeqlPort.CreateDbResult db = codeqlPort.createDatabase(new CodeqlPort.CreateDbRequest(
            tr.id(),
            m.id(),
            sourceRootPath,
            m.rootPath(),
            "java",
            buildCmd,
            codeqlDockerImageFor(m),
            "codeql-db",
            Duration.ofMinutes(600) //반드시 일단 600 추후 수정
        ));

        storeLocalPathArtifact(tr.id(), ArtifactType.CODEQL_DB, "codeql-db", db.dbDirPathOnHost());
        storeTextArtifact(tr.id(), ArtifactType.OTHER, "codeql-db-stdout.log", db.stdout());
        storeTextArtifact(tr.id(), ArtifactType.OTHER, "codeql-db-stderr.log", db.stderr());

        CodeqlPort.RunQueriesResult sarif = codeqlPort.runQueries(new CodeqlPort.RunQueriesRequest(
            tr.id(),
            m.id(),
            codeqlDockerImageFor(m),
            db.dbDirPathOnHost(),
            List.of("codeql/java-queries"),
            "result.sarif",
            Duration.ofMinutes(120)
        ));

        Path sarifHostPath = Path.of(sarif.sarifPathOnHost());
        String sarifStorageUri = storeFileArtifact(tr.id(), ArtifactType.OTHER, "result.sarif", sarifHostPath);

        storeTextArtifact(tr.id(), ArtifactType.OTHER, "codeql-analyze-stdout.log", sarif.stdout());
        storeTextArtifact(tr.id(), ArtifactType.OTHER, "codeql-analyze-stderr.log", sarif.stderr());

        //ADDED: SARIF -> DB 적재 호출 (0건이면 CLEAN summary만 저장)
        codeqlSarifIngestService.ingest(
            tr.id(),
            m.id(),
            sarifHostPath.toString(),
            sarifStorageUri
        );
      });
    }
  }

  /**
   * CodeQL DB 생성 시 사용할 빌드 커맨드를 만든다.
   *
   * @param m 서비스 모듈
   * @return build command 문자열
   */
  private String codeqlBuildCommandFor(ServiceModule m) {
    String rel = shellEscapePath(m.rootPath());
    String targetDir = (rel == null || rel.isBlank())
        ? "/src"
        : "/src/" + rel;

    return switch (m.buildTool()) {
      case MAVEN  -> "cd " + targetDir + " && mvn -DskipTests package";
      case GRADLE -> "cd " + targetDir + " && ./gradlew build -x test";
      case JAR    -> "echo 'skip build for JAR'";
      default     -> "echo 'skip build for OTHER'";
    };
  }

  /**
   * 서비스 모듈에 맞는 CodeQL Docker 이미지를 선택한다.
   *
   * @param m 서비스 모듈
   * @return docker image name
   */
  private String codeqlDockerImageFor(ServiceModule m) {
    String image = toolImageConfig.codeqlImage();

    // 로그로 찍기
    log.info("[PIPE] CodeQL image resolved. serviceModuleId={}, name={}, image={}",
        m.id(), m.name(), image);

    if (image == null || image.isBlank()) {
        throw new IllegalStateException(
            "CodeQL docker image not configured. " +
            "Please set 'sca.tool.images.codeql' in application.yml"
        );
    }
    return image;
  }
  /**
   * 쉘 커맨드에서 경로로 사용할 문자열을 최소한으로 escape 한다.
   *
   * @param relPath 상대 경로
   * @return escape된 문자열
   */
  private String shellEscapePath(String relPath) {
    if (relPath == null) return "";
    return relPath.replace("\"", "\\\"");
  }

  /////////////////////////////////////////////////////////////////////////////////////
  /// AGENT 메서드
  /////////////////////////////////////////////////////////////////////////////////////

  /**
   * AGENT 단계를 실행한다(프로젝트 버전 단위 1회).
   *
   * @param analysisRunId analysis_run ID
   * @param projectVersionId project_version ID
   * @param sourceRootPath 소스 루트 경로
   */
  private void runAgentStage(Long analysisRunId, Long projectVersionId, String sourceRootPath) {
    ToolRun tr = toolRunCommandPort.createAgentRun(analysisRunId, "gpt-model-placeholder", "agent", agentConfigJson());
    runTool(tr.id(), () -> {
      agentPort.buildSanitizerRegistry(tr.id(), projectVersionId, sourceRootPath);
      storeTextArtifact(tr.id(), ArtifactType.AGENT_OUTPUT, "agent-output.txt", "agent output placeholder");
    });
  }

  /////////////////////////////////////////////////////////////////////////////////////
  /// MSCAN 메서드
  /////////////////////////////////////////////////////////////////////////////////////

  private void initMscanJarDir(String sourceRootPath) {
    try {
      Path jarDir = Path.of(sourceRootPath).resolve(MSCAN_JAR_DIR_REL_PATH);
      Files.createDirectories(jarDir);

      try (Stream<Path> s = Files.list(jarDir)) {
        s.filter(Files::isRegularFile).forEach(p -> {
          try { Files.deleteIfExists(p); } catch (Exception ignored) {}
        });
      }
    } catch (Exception e) {
      throw new IllegalStateException("Failed to init mscan jar dir", e);
    }
  }

  private void ensureMscanJarDirHasJars(Path jarDir) {
    try (Stream<Path> s = Files.list(jarDir)) {
      boolean hasJar = s.anyMatch(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".jar"));
      if (!hasJar) {
        throw ToolExecutionException.mscan(null, "MScan jar dir is empty: " + jarDir + " (no *.jar staged)");
      }
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw ToolExecutionException.mscan(null, "Failed to inspect mscan jar dir: " + jarDir, e);
    }
  }

  /**
   * MSCAN 단계를 실행한다(프로젝트 버전 단위 1회).
   *
   * @param analysisRunId analysis_run ID
   * @param projectVersionId project_version ID
   * @param sourceRootPath 소스 루트 경로
   */
  private void runMscanStage(AnalysisRun run, String sourceRootPath, List<ServiceModule> modules) {

    //  1) tool_run을 먼저 만든다 (이후 어떤 실패도 tool_run FAILED로 남게 됨)
    ToolRun tr = toolRunCommandPort.createMscanRun(run.id(), "mscan", mscanConfigJson());

    runTool(tr.id(), () -> {

      // 2) jars dir 준비 + 비어있으면 storage에서 끌어모으기
      Path jarDir = Path.of(sourceRootPath).resolve(MSCAN_JAR_DIR_REL_PATH);
      if (!Files.isDirectory(jarDir)) {
        try {
          Files.createDirectories(jarDir);
        } catch (java.io.IOException e) {
          throw ToolExecutionException.mscan(tr.id(), "Failed to create mscan jar dir: " + jarDir, e);
        }
      }

      // jarDir이 비어있으면(또는 jar가 없으면) storage에서 모아오기
      if (!hasAnyJar(jarDir)) {
        collectMscanJarsFromStorage(sourceRootPath, modules);
      }

      // 최종 확인: 그래도 없으면 실패(이제는 MSCAN tool_run에 실패 기록이 남음)
      ensureMscanJarDirHasJars(jarDir);

      // 3) config_json에서 mscan 설정 읽기 (여기서 실패해도 MSCAN tool_run에 남음)
      var cfg = run.configJson();
      String name = (cfg == null) ? null : cfg.path("mscan").path("name").asText(null);
      String keywords = (cfg == null) ? null : cfg.path("mscan").path("classpathKeywords").asText(null);
      String jvmArgs = (cfg == null) ? null : cfg.path("mscan").path("jvmArgs").asText(null);
      boolean reuse = (cfg != null) && cfg.path("mscan").path("reuse").asBoolean(false);
      String optionsRel = (cfg == null) ? null : cfg.path("mscan").path("optionsFileRelPath").asText(null);

      final String optionsFilePathOnHost =
          (optionsRel == null || optionsRel.isBlank())
              ? null
              : Path.of(sourceRootPath).resolve(optionsRel).toString();

      if (name == null || name.isBlank()) {
        throw new InvalidConfigException("Missing analysis_run.config_json: mscan.name");
      }
      if (keywords == null || keywords.isBlank()) {
        throw new InvalidConfigException("Missing analysis_run.config_json: mscan.classpathKeywords");
      }

      // 4) gateway.yml 경로(캐시에 재물질화되어 있어야 함)
      String gatewayOnHost = Path.of(sourceRootPath).resolve(MSCAN_GATEWAY_REL_PATH).toString();
      if (!Files.exists(Path.of(gatewayOnHost))) {
        throw new InvalidConfigException("MScan gateway.yml not found in cache: " + gatewayOnHost);
      }

      // 5) 이미지/키 확인
      String mscanImage = toolImageConfig.mscanImage();
      if (mscanImage == null || mscanImage.isBlank()) {
        throw new InvalidConfigException("MScan docker image not configured. Set sca.tool.images.mscan");
      }

      String llmApiKey = toolLlmConfig.openAiApiKey();
      String baseUrl    = toolLlmConfig.openAiBaseUrl();
      String model      = toolLlmConfig.openAiModel();

      if (llmApiKey == null || llmApiKey.isBlank()) {
        throw new InvalidConfigException("OPENAI API key not configured. Set sca.tool.llm.openai-api-key");
      }

      dockerImagePort.ensurePresent(mscanImage, Duration.ofMinutes(20));
      
      // 6) 실제 mscan 실행
      var res = mscanPort.runGlobalAnalysis(new MscanPort.RunRequest(
          tr.id(),
          run.projectVersionId(),
          name,
          sourceRootPath,
          gatewayOnHost,
          jarDir.toString(),
          keywords,
          optionsFilePathOnHost,
          reuse,
          jvmArgs,
          mscanImage,
          llmApiKey,
          baseUrl,
          model,
          Duration.ofMinutes(120)
      ));

      // stdout/stderr 저장
      storeTextArtifact(tr.id(), ArtifactType.OTHER, "mscan-stdout.log", res.stdout());
      storeTextArtifact(tr.id(), ArtifactType.OTHER, "mscan-stderr.log", res.stderr());

      // 7) report 업로드 + artifact 등록
      Path report = Path.of(res.reportPathOnHost());
      String reportSha = computeSha256Hex(report);

      String reportKey = "runs/toolRun/" + tr.id() + "/mscan/report.txt";
      try (InputStream in = Files.newInputStream(report)) {
        var stored = storagePort.put(reportKey, in);

        artifactPort.create(
            tr.id(),
            ArtifactType.MSCAN_REPORT,
            stored.uri(),
            om.createObjectNode()
                .put("filename", "report.txt")
                .put("sha256", reportSha)
        );

        int count = countNonEmptyLines(report);

        // 개별 finding 파싱 및 저장
        List<MscanResultPort.MscanFindingIngest> findings = parseMscanReport(report);
        mscanResultPort.replaceAll(tr.id(), findings);

        var status = (count == 0)
            ? MscanSummaryStatus.CLEAN
            : MscanSummaryStatus.HAS_RESULTS;

        mscanRunSummaryCommandPort.upsert(
            tr.id(),
            status,
            count,
            stored.uri(),
            reportSha,
            java.time.Instant.now()
        );

      } catch (Exception e) {
        mscanRunSummaryCommandPort.upsert(
            tr.id(),
            MscanSummaryStatus.INGEST_FAILED,
            0,
            null,
            null,
            java.time.Instant.now()
        );
        throw ToolExecutionException.mscan(
            tr.id(),
            "MScan ingest failed: " + e.getMessage(),
            e
        );
      }
    });
  }

  private boolean hasAnyJar(Path jarDir) {
    try (var s = Files.list(jarDir)) {
      return s.anyMatch(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".jar"));
    } catch (Exception e) {
      return false;
    }
  }

  private int countNonEmptyLines(Path p) {
    try (Stream<String> s = Files.lines(p, StandardCharsets.UTF_8)) {
      return (int) s.filter(line -> line != null && !line.isBlank()).count();
    } catch (Exception e) {
      // 파싱 자체 실패는 ingest 실패로 보는 게 맞아서 예외로 올림
      throw ToolExecutionException.mscan(null, "Failed to read mscan report: " + p, e);
    }
  }

  private List<MscanResultPort.MscanFindingIngest> parseMscanReport(Path reportPath) {
    try (Stream<String> lines = Files.lines(reportPath, StandardCharsets.UTF_8)) {
      List<MscanResultPort.MscanFindingIngest> result = new ArrayList<>();
      lines.forEach(raw -> {
        if (raw == null) {
          return;
        }
        String line = raw.trim();
        if (line.isBlank()) {
          return;
        }

        // 1) flowIndex (맨 앞 숫자)
        int spaceIdx = line.indexOf(' ');
        if (spaceIdx <= 0) {
          return;
        }
        int flowIndex;
        try {
          flowIndex = Integer.parseInt(line.substring(0, spaceIdx));
        } catch (NumberFormatException e) {
          return;
        }

        String rest = line.substring(spaceIdx + 1).trim();

        // 2) VUL_ID 추출
        String vulId = "UNKNOWN";
        String flowText = rest;
        int vulPos = rest.indexOf("VUL_ID:");
        if (vulPos >= 0) {
          String after = rest.substring(vulPos + "VUL_ID:".length());
          vulId = after.replaceAll("[}\\s]+$", "").trim();
          flowText = rest.substring(0, vulPos).trim();
        }

        // 3) source / sink signature 대충 분리
        String sourceSig = flowText;
        String sinkSig = flowText;
        int tfPos = flowText.indexOf("TaintFlow{");
        if (tfPos >= 0) {
          int arrowPos = flowText.indexOf("->", tfPos);
          String inner = flowText.substring(tfPos + "TaintFlow{".length());
          int closeBrace = inner.lastIndexOf('}');
          if (closeBrace > 0) {
            inner = inner.substring(0, closeBrace);
          }
          int arrowInInner = inner.indexOf("->");
          if (arrowInInner > 0) {
            sourceSig = inner.substring(0, arrowInInner).trim();
            sinkSig = inner.substring(arrowInInner + 2).trim();
          } else {
            sourceSig = inner.trim();
          }
        }

        result.add(new MscanResultPort.MscanFindingIngest(
            flowIndex,
            sourceSig,
            sinkSig,
            vulId,
            line
        ));
      });
      return result;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to parse mscan report for findings: " + reportPath, e);
    }
  }

  private Path prepareMscanJarDir(String sourceRootPath, List<ServiceModule> modules) {
    try {
      Path jarDir = Path.of(sourceRootPath).resolve(MSCAN_JAR_DIR_REL_PATH);
      Files.createDirectories(jarDir);

      // 기존에 이미 구현해둔 jar 후보 수집 로직 재사용
      for (ServiceModule m : modules) {
        Path moduleDir = Path.of(normalizeDir(sourceRootPath, m.rootPath()));
        List<Path> candidates = findJarCandidates(moduleDir, m);

        for (Path jar : candidates) {
          // 파일명 충돌 시 moduleId prefix로 방지
          String outName = "sm-" + m.id() + "-" + jar.getFileName().toString();
          Files.copy(jar, jarDir.resolve(outName), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
      }

      return jarDir;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to prepare mscan jar dir", e);
    }
  }

  /** jarDir이 sourceRoot 하위인지 보장(컨테이너 mount를 sourceRoot 하나만 할 거라서) */
  private void assertUnderSourceRoot(String sourceRootPath, Path p) {
    Path root = Path.of(sourceRootPath).toAbsolutePath().normalize();
    Path abs  = p.toAbsolutePath().normalize();
    if (!abs.startsWith(root)) {
      throw new IllegalStateException("Path must be under sourceRoot. path=" + abs + ", root=" + root);
    }
  }

  /**
   * report.json의 결과 개수 파싱.
   * (실제 MScan 리포트 스키마에 맞춰 수정)
   */
  private int tryCountMscanFindings(Path reportJson) {
    try {
      var node = om.readTree(Files.readString(reportJson));
      // 예: { "flows": [ ... ] } 형태 가정
      if (node.has("flows") && node.get("flows").isArray()) {
        return node.get("flows").size();
      }
      // 혹은 { "resultCount": 0 }
      if (node.has("resultCount")) {
        return node.get("resultCount").asInt(0);
      }
      return 0;
    } catch (Exception e) {
      // 파싱 실패는 ingest 실패로 처리하는 게 정석이므로 예외 던짐
      throw new IllegalStateException("Invalid mscan report format: " + reportJson, e);
    }
  }
  
  /**
   * Gateway 모듈이 존재하면 gateway.yml이 READY여야 한다.
   * READY가 아니면:
   *  1) 소스(cache)에서 gateway application.yml(yaml) 자동 탐색 시도
   *  2) 찾으면 Storage에 업로드 + DB에 gateway.yml READY로 등록(upsertReady)
   *  3) 그래도 못 찾으면 DB에 MISSING ensure 후 실패(사용자 업로드 요구)
   */
  private void requireGatewayModuleAndYaml(Long projectVersionId, String sourceRootPath, List<ServiceModule> modules) {
    Path cachePath = Path.of(sourceRootPath)
        .resolve(MSCAN_GATEWAY_REL_PATH)
        .toAbsolutePath()
        .normalize();

    // 0) 캐시에 이미 있으면 OK
    if (Files.exists(cachePath) && Files.isRegularFile(cachePath)) {
      return;
    }

    // 1) DB에 READY가 있으면 storage -> cache 재물질화
    var opt = mscanGatewayYamlPort.findByProjectVersionId(projectVersionId);
    if (opt.isPresent() && opt.get().status() == GatewayYamlStatus.READY) {
      materializeGatewayYamlToCache(opt.get(), sourceRootPath);

      if (Files.exists(cachePath) && Files.isRegularFile(cachePath)) {
        return;
      }
      throw new IllegalStateException("gateway.yml READY but not materialized: " + cachePath);
    }

    // 2) auto-detect는 is_gateway=true 모듈에서만 (오탐 방지)
    List<ServiceModule> gatewayModules = modules.stream().filter(ServiceModule::isGateway).toList();
    if (gatewayModules.isEmpty()) {
      mscanGatewayYamlCommandPort.ensureMissing(projectVersionId, MSCAN_GATEWAY_REL_PATH);
      throw new IllegalStateException(
          "MScan requires gateway.yml but gateway module was not detected (is_gateway=false for all). " +
          "Please upload/generate gateway.yml for projectVersionId=" + projectVersionId
      );
    }

    // 3) 여기까지 못 찾았으면 업로드 요구
    mscanGatewayYamlCommandPort.ensureMissing(projectVersionId, MSCAN_GATEWAY_REL_PATH);

    throw new IllegalStateException(
        "MScan requires gateway.yml but it is missing. " +
        "No READY record and auto-detect failed. " +
        "Please upload gateway.yml for projectVersionId=" + projectVersionId
    );
  }

  /** storage에 저장된 gateway.yml을 cache_rel_path 위치로 복사 */
  private void materializeGatewayYamlToCache(MscanGatewayYaml yaml, String sourceRootPath) {
    if (yaml.storagePath() == null || yaml.storagePath().isBlank()) {
        throw new IllegalStateException("gateway yaml storagePath is empty");
    }

    try {
        Path target = Path.of(sourceRootPath).resolve(yaml.cacheRelPath()).normalize();
        Files.createDirectories(target.getParent());

        try (InputStream in = storagePort.open(yaml.storagePath())) {
            Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            throw new IllegalStateException("gateway.yml was not created at: " + target);
        }

    } catch (Exception e) {
        throw new IllegalStateException("Failed to materialize gateway.yml into source cache", e);
    }
  }

  /////////////////////////////////////////////////////////////////////////////////////
  /// Artifact/Config 헬퍼
  /////////////////////////////////////////////////////////////////////////////////////

  /**
   * 문자열 산출물을 스토리지에 저장하고 analysis_artifact로 기록한다.
   *
   * @param toolRunId tool_run ID
   * @param type artifact type
   * @param filename 파일명
   * @param content 파일 내용
   */
  private void storeTextArtifact(Long toolRunId, ArtifactType type, String filename, String content) {
    String key = "runs/toolRun/" + toolRunId + "/" + filename;
    var stored = storagePort.put(key, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));

    ObjectNode meta = om.createObjectNode();
    meta.put("filename", filename);
    meta.put("length", content.length());

    artifactPort.create(toolRunId, type, stored.uri(), meta);
  }

  private String storeFileArtifact(Long toolRunId, ArtifactType type, String filename, Path fileOnHost) {
    try (InputStream in = Files.newInputStream(fileOnHost)) {
      String key = "runs/toolRun/" + toolRunId + "/" + filename;
      var stored = storagePort.put(key, in);

      ObjectNode meta = om.createObjectNode();
      meta.put("filename", filename);
      meta.put("hostPath", fileOnHost.toString().replace("\\", "/"));
      meta.put("size", Files.size(fileOnHost));

      artifactPort.create(toolRunId, type, stored.uri(), meta);
      return stored.uri();
    } catch (Exception e) {
      storeTextArtifact(toolRunId, ArtifactType.OTHER, "artifact-store-error.log",
          "Failed to store file artifact: " + fileOnHost + "\n" + e);
      throw new IllegalStateException("Failed to store file artifact: " + fileOnHost, e);
    }
  }

  /**
   * 로컬 파일/디렉토리 경로를 artifact로 기록한다.
   *
   * @param toolRunId tool_run ID
   * @param type artifact type
   * @param name 의미 있는 이름
   * @param pathOnHost 호스트 경로
   */
  private void storeLocalPathArtifact(Long toolRunId, ArtifactType type, String name, String pathOnHost) {
    ObjectNode meta = om.createObjectNode();
    meta.put("name", name);
    meta.put("hostPath", pathOnHost);

    artifactPort.create(toolRunId, type, "file://" + pathOnHost, meta);
  }

  /**
   * BUILD config_json 생성.
   *
   * @param m 서비스 모듈
   * @param image docker image 널 허용
   * @return config json
   */
  private ObjectNode buildConfigJson(ServiceModule m, String image) {
    ObjectNode n = om.createObjectNode();
    n.put("serviceModuleId", m.id());
    n.put("buildTool", m.buildTool().name());
    n.put("rootPath", m.rootPath());
    n.put("jdkVersion", m.jdkVersion() == null ? "" : m.jdkVersion());

    if (image != null && !image.isBlank()) {
      n.put("dockerImage", image);
    }

    return n;
  }

  /**
   * CODEQL config_json 생성.
   *
   * @param m 서비스 모듈
   * @return config json
   */
  private ObjectNode codeqlConfigJson(ServiceModule m) {
    ObjectNode n = om.createObjectNode();
    n.put("serviceModuleId", m.id());
    n.put("mode", "default");
    return n;
  }

  /**
   * AGENT config_json 생성.
   *
   * @return config json
   */
  private ObjectNode agentConfigJson() {
    ObjectNode n = om.createObjectNode();
    n.put("mode", "sanitizer-registry");
    return n;
  }

  /**
   * MSCAN config_json 생성.
   *
   * @return config json
   */
  private ObjectNode mscanConfigJson() {
    ObjectNode n = om.createObjectNode();
    n.put("mode", "global");
    return n;
  }

  /**
   * base/rel을 결합하여 작업 디렉토리 경로를 만든다.
   *
   * @param base base path
   * @param rel rel path
   * @return joined path
   */
  private String normalizeDir(String base, String rel) {
    if (rel == null || rel.isBlank()) return base;
    if (base.endsWith("/")) return base + rel;
    return base + "/" + rel;
  }
}