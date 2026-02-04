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
import buaa.msasca.sca.core.port.out.tool.BuildImageResolver;
import buaa.msasca.sca.core.port.out.tool.BuildPort;
import buaa.msasca.sca.core.port.out.tool.CodeqlPort;
import buaa.msasca.sca.core.port.out.tool.MscanPort;
import buaa.msasca.sca.core.port.out.tool.StoragePort;

public class PipelineExecutor {

  private final AnalysisRunPort analysisRunPort;
  private final ServiceModulePort serviceModulePort;
  private final ProjectVersionSourceCachePort sourceCachePort;

  private final ToolRunCommandPort toolRunCommandPort;
  private final ToolRunPort toolRunPort;
  private final AnalysisArtifactPort artifactPort;

  private final StoragePort storagePort;

  private final BuildPort buildPort;
  private final BuildImageResolver buildImageResolver;

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
      StoragePort storagePort,
      BuildPort buildPort,
      BuildImageResolver buildImageResolver,
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
    this.storagePort = storagePort;
    this.buildPort = buildPort;
    this.buildImageResolver = buildImageResolver;
    this.codeqlPort = codeqlPort;
    this.agentPort = agentPort;
    this.mscanPort = mscanPort;
  }

  /**
   * 하나의 analysis_run을 end-to-end로 실행한다.
   * 실행 흐름:
   * 1) 입력 조회/검증(PENDING만 실행)
   * 2) source cache 조회
   * 3) service module 조회
   * 4) RUNNING 전이
   * 5) 단계 실행(빌드→코드큐엘→에이전트→엠스캔)  ※ 순서는 여기서 바꾸면 됨
   * 6) 성공 시 DONE, 실패 시 FAILED
   *
   * @param analysisRunId 실행할 analysis_run ID
   */
  public void execute(Long analysisRunId) {
    AnalysisRun run = loadPendingRunOrReturn(analysisRunId);
    if (run == null) return;

    ProjectVersionSourceCache cache = loadSourceCacheOrThrow(run.projectVersionId());
    String sourceRootPath = cache.storagePath();

    List<ServiceModule> modules = serviceModulePort.findByProjectVersionId(run.projectVersionId());

    analysisRunPort.markRunning(analysisRunId);

    try {
      /////////////////////////////////////////////////////////////////////////////////////
      /// (현재) 단계 순서: BUILD -> CODEQL -> AGENT -> MSCAN
      /// 사용자가 제시한 최종 순서로 바꾸려면 아래 호출 순서만 재배치하면 됨.
      /////////////////////////////////////////////////////////////////////////////////////

      runBuildStage(analysisRunId, modules, sourceRootPath);

      runCodeqlStage(analysisRunId, modules, sourceRootPath);

      runAgentStage(analysisRunId, run.projectVersionId(), sourceRootPath);

      runMscanStage(analysisRunId, run.projectVersionId(), sourceRootPath);

      analysisRunPort.markDone(analysisRunId);
    } catch (Exception e) {
      analysisRunPort.markFailed(analysisRunId);
      throw e;
    }
  }

  /**
   * analysis_run을 조회하고 PENDING이 아니면 null을 반환한다.
   *
   * @param analysisRunId analysis_run ID
   * @return PENDING이면 AnalysisRun, 아니면 null
   */
  private AnalysisRun loadPendingRunOrReturn(Long analysisRunId) {
    AnalysisRun run = analysisRunPort.findById(analysisRunId)
        .orElseThrow(() -> new IllegalArgumentException("analysis_run not found: " + analysisRunId));

    if (run.status() != RunStatus.PENDING) {
      return null;
    }
    return run;
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

  /////////////////////////////////////////////////////////////////////////////////////
  /// BUILD 메서드
  /////////////////////////////////////////////////////////////////////////////////////

    /**
     * BUILD 단계를 실행한다(서비스 모듈 단위).
     * - tool_run 생성 후
     * - BuildImageResolver로 이미지 선택(모듈 디렉토리 파일 기반)
     * - BuildPort로 Docker 빌드 수행
     * - 빌드 성공 시 JAR 자동 수집
     *
     * @param analysisRunId analysis_run ID
     * @param modules 서비스 모듈 목록
     * @param sourceRootPath 소스 루트 경로
     */
    private void runBuildStage(Long analysisRunId, List<ServiceModule> modules, String sourceRootPath) {
      for (ServiceModule m : modules) {

        ToolRun tr = toolRunCommandPort.createBuildRun(
            analysisRunId,
            m.id(),
            "docker-build",
            buildConfigJson(m, null) // 아직 이미지 미확정
        );

        runTool(tr.id(), () -> {
          Path moduleDir = Path.of(normalizeDir(sourceRootPath, m.rootPath()));

          //파일 기반 이미지 선택
          String image = buildImageResolver.resolve(m, moduleDir);

          // 선택된 이미지는 로그로 남기기
          storeTextArtifact(tr.id(), ArtifactType.OTHER, "build-image.log", "image=" + image);

          buildModule(tr.id(), m, sourceRootPath, image);
        });
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
    collectAndStoreJars(toolRunId, module, sourceRootPath);
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
   * - Maven: <module>/target/*.jar
   * - Gradle: <module>/build/libs/*.jar
   * - 위 경로에서 못 찾으면: <module> 하위 제한 깊이로 fallback 탐색
   *
   * primary JAR 선정 규칙(대략):
   * 1) sources/javadoc/tests/original/plain 제외
   * 2) 크기가 큰 것 우선
   * 3) 수정 시간이 최신인 것 우선
   *
   * @param toolRunId build tool_run ID (JAR artifact는 build tool_run에 귀속)
   * @param module 서비스 모듈
   * @param sourceRootPath 소스 루트 경로
   */
  private void collectAndStoreJars(Long toolRunId, ServiceModule module, String sourceRootPath) {
    Path moduleDir = Path.of(normalizeDir(sourceRootPath, module.rootPath()));
    if (!Files.exists(moduleDir) || !Files.isDirectory(moduleDir)) {
      storeTextArtifact(toolRunId, ArtifactType.OTHER, "jar-collect.log",
          "Module dir not found: " + moduleDir);
      return;
    }

    List<Path> candidates = findJarCandidates(moduleDir, module);
    if (candidates.isEmpty()) {
      storeTextArtifact(toolRunId, ArtifactType.OTHER, "jar-collect.log",
          "No jar found under moduleDir=" + moduleDir);
      return;
    }

    Optional<Path> primary = choosePrimaryJar(candidates);

    int uploaded = 0;
    for (Path jar : candidates) {
      boolean isPrimary = primary.isPresent() && primary.get().equals(jar);
      storeJarArtifact(toolRunId, module, moduleDir, jar, isPrimary);
      uploaded++;
    }

    storeTextArtifact(toolRunId, ArtifactType.OTHER, "jar-collect.log",
        "Collected jars=" + uploaded + ", primary=" + primary.map(Path::toString).orElse("none"));
  }

  /**
   * 서비스 모듈 디렉토리에서 JAR 후보를 수집한다.
   * 기본 산출 위치(target, build/libs)를 우선 탐색하고, 없으면 fallback으로 제한 깊이 탐색을 수행한다.
   *
   * @param moduleDir 모듈 루트 디렉토리
   * @param module 서비스 모듈
   * @return JAR 후보 리스트(필터링 적용)
   */
  private List<Path> findJarCandidates(Path moduleDir, ServiceModule module) {
    List<Path> result = new ArrayList<>();

    // 1) 기본 디렉토리 우선 탐색
    List<Path> preferredDirs = new ArrayList<>();
    switch (module.buildTool()) {
      case MAVEN -> preferredDirs.add(moduleDir.resolve("target"));
      case GRADLE -> preferredDirs.add(moduleDir.resolve("build").resolve("libs"));
      default -> {
        preferredDirs.add(moduleDir.resolve("target"));
        preferredDirs.add(moduleDir.resolve("build").resolve("libs"));
      }
    }

    for (Path dir : preferredDirs) {
      if (Files.exists(dir) && Files.isDirectory(dir)) {
        result.addAll(listJarsInDir(dir));
      }
    }

    // 2) preferred에서 못 찾으면 fallback(깊이 제한)
    if (result.isEmpty()) {
      result.addAll(findJarsByWalk(moduleDir, 5));
    }

    // 3) 필터 적용
    return result.stream()
        .filter(this::isValidJarCandidate)
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
    try (Stream<Path> s = Files.list(dir)) {
      return s.filter(Files::isRegularFile)
          .filter(p -> p.getFileName().toString().endsWith(".jar"))
          .toList();
    } catch (Exception e) {
      return List.of();
    }
  }

  /**
   * 모듈 디렉토리를 제한 깊이로 walk 하며 *.jar를 수집한다.
   *
   * @param moduleDir 모듈 루트
   * @param maxDepth 최대 깊이
   * @return jar 파일 리스트
   */
  private List<Path> findJarsByWalk(Path moduleDir, int maxDepth) {
    try (Stream<Path> s = Files.find(
        moduleDir,
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
   * - sources/javadoc/tests 계열 제외
   * - Spring Boot maven plugin의 original-*.jar 제외(보통 원본 jar)
   * - Gradle의 *-plain.jar 제외(bootJar가 존재하는 경우가 많음)
   *
   * @param jarPath jar 파일 경로
   * @return 후보로 인정하면 true
   */
  private boolean isValidJarCandidate(Path jarPath) {
    String name = jarPath.getFileName().toString();

    if (!name.endsWith(".jar")) return false;

    if (name.endsWith("-sources.jar")) return false;
    if (name.endsWith("-javadoc.jar")) return false;
    if (name.endsWith("-tests.jar")) return false;
    if (name.endsWith("-test.jar")) return false;

    if (name.startsWith("original-")) return false;
    if (name.endsWith("-plain.jar")) return false;

    return true;
  }

  /**
   * 여러 후보 중 primary JAR 1개를 선정한다.
   * 우선순위: (크기 DESC) -> (수정시간 DESC) -> (이름 ASC)
   *
   * @param candidates 후보 리스트(필터링 후)
   * @return primary 후보
   */
  private Optional<Path> choosePrimaryJar(List<Path> candidates) {
    return candidates.stream()
        .max(Comparator
            .comparingLong(this::safeSize)
            .thenComparing(this::safeMtime, Comparator.naturalOrder())
            .thenComparing(p -> p.getFileName().toString())
        );
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
        meta.put("relativePath", moduleDir.relativize(jarPath).toString());

        artifactPort.create(toolRunId, ArtifactType.JAR, stored.uri(), meta);
      }
    } catch (Exception e) {
      storeTextArtifact(toolRunId, ArtifactType.OTHER,
          "jar-collect-error-" + jarPath.getFileName() + ".log",
          "Failed to upload jar: " + jarPath + "\n" + e);
      throw new IllegalStateException("Failed to store jar artifact: " + jarPath, e);
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
    n.put("dockerImage", image);

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