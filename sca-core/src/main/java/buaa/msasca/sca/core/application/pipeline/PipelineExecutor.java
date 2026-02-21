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
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import buaa.msasca.sca.core.domain.enums.ArtifactType;
import buaa.msasca.sca.core.domain.model.AnalysisRun;
import buaa.msasca.sca.core.domain.model.ProjectVersionSourceCache;
import buaa.msasca.sca.core.domain.model.ServiceModule;
import buaa.msasca.sca.core.domain.model.ToolRun;
import buaa.msasca.sca.core.port.out.persistence.AnalysisArtifactPort;
import buaa.msasca.sca.core.port.out.persistence.AnalysisRunCommandPort;
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

  private final CodeqlPort codeqlPort;
  private final AgentPort agentPort;
  private final MscanPort mscanPort;

  private final ServiceModuleScannerPort serviceModuleScannerPort;
  private final ServiceModuleCommandPort serviceModuleCommandPort;


  private final ObjectMapper om = new ObjectMapper();

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
      ServiceModuleCommandPort serviceModuleCommandPort
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

    try {
      //모듈별 이미지 결정(파일 기반) + distinct 이미지 선-ensure
      
      //log.info("[PIPE] step4 prepareBuildPlans start. modules={}", modules.size());
      List<BuildPlan> buildPlans = prepareBuildPlans(modules, sourceRootPath);
      // log.info("[PIPE] step4 prepareBuildPlans done. plans={}, images={}",
      //     buildPlans.size(),
      //     buildPlans.stream().map(BuildPlan::image).distinct().toList()
      // );

      //log.info("[PIPE] step5 ensureBuildImages start");
      ensureBuildImages(buildPlans, Duration.ofMinutes(20));
      //log.info("[PIPE] step5 ensureBuildImages done");


      /////////////////////////////////////////////////////////////////////////////////////
      /// (현재) 단계 순서: BUILD -> CODEQL -> AGENT -> MSCAN
      /// 사용자가 제시한 최종 순서로 바꾸려면 아래 호출 순서만 재배치하면 됨.
      /////////////////////////////////////////////////////////////////////////////////////

      //log.info("[PIPE] step6 runBuildStage start");
      runBuildStage(analysisRunId, buildPlans, sourceRootPath);

      // runCodeqlStage(analysisRunId, modules, sourceRootPath);

      // runAgentStage(analysisRunId, run.projectVersionId(), sourceRootPath);

      // runMscanStage(analysisRunId, run.projectVersionId(), sourceRootPath);

    } catch (Exception e) {
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
      toolRunPort.markFailed(toolRunId, msg);
      throw e;
    }
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

    // 빌드 성공 시 JAR 자동 수집
    collectAndStoreJars(toolRunId, module, res.builtSourceRootPathOnHost());
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
  private void collectAndStoreJars(Long toolRunId, ServiceModule module, String sourceRootPath) {
    Path moduleDir = Path.of(normalizeDir(sourceRootPath, module.rootPath()));
    
    //로그찍기
    storeTextArtifact(toolRunId, ArtifactType.OTHER, "jar-collect-root.log",
    "collectRoot=" + sourceRootPath + ", moduleRootPath=" + module.rootPath() + ", moduleDir=" + moduleDir);

    if (!Files.exists(moduleDir) || !Files.isDirectory(moduleDir)) {
      storeTextArtifact(toolRunId, ArtifactType.OTHER, "jar-collect.log",
          "Module dir not found: " + moduleDir);
      return;
    }

    List<Path> candidates = findJarCandidates(moduleDir, module);
    if (candidates.isEmpty()) {
      storeTextArtifact(toolRunId, ArtifactType.OTHER, "jar-collect.log",
          "No jar found. moduleDir=" + moduleDir
              + ", buildTool=" + module.buildTool()
              + ", searched=[build/libs,target]");
      return;
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
            Duration.ofMinutes(60)
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
            Duration.ofMinutes(60)
        ));

        storeLocalPathArtifact(tr.id(), ArtifactType.OTHER, "codeql-result-sarif", sarif.sarifPathOnHost());
        storeTextArtifact(tr.id(), ArtifactType.OTHER, "codeql-analyze-stdout.log", sarif.stdout());
        storeTextArtifact(tr.id(), ArtifactType.OTHER, "codeql-analyze-stderr.log", sarif.stderr());
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
    return switch (m.buildTool()) {
      case MAVEN -> "cd " + shellEscapePath(m.rootPath()) + " && mvn -DskipTests package";
      case GRADLE -> "cd " + shellEscapePath(m.rootPath()) + " && ./gradlew build -x test";
      case JAR -> "echo 'skip build for JAR'";
      default -> "echo 'skip build for OTHER'";
    };
  }

  /**
   * 서비스 모듈에 맞는 CodeQL Docker 이미지를 선택한다.
   *
   * @param m 서비스 모듈
   * @return docker image name
   */
  private String codeqlDockerImageFor(ServiceModule m) {
    return "YOUR_CODEQL_IMAGE";
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

  /**
   * MSCAN 단계를 실행한다(프로젝트 버전 단위 1회).
   *
   * @param analysisRunId analysis_run ID
   * @param projectVersionId project_version ID
   * @param sourceRootPath 소스 루트 경로
   */
  private void runMscanStage(Long analysisRunId, Long projectVersionId, String sourceRootPath) {
    ToolRun tr = toolRunCommandPort.createMscanRun(analysisRunId, "mscan", mscanConfigJson());
    runTool(tr.id(), () -> {
      mscanPort.runGlobalAnalysis(tr.id(), projectVersionId, sourceRootPath);
      storeTextArtifact(tr.id(), ArtifactType.MSCAN_REPORT, "mscan-report.txt", "mscan-report placeholder");
    });
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