package buaa.msasca.sca.tool.codeql;

import buaa.msasca.sca.core.port.out.tool.CodeqlPort;
import buaa.msasca.sca.core.port.out.tool.RunnerPort;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class DockerCodeqlPortAdapter implements CodeqlPort {

  private final RunnerPort runnerPort;
  private final String workspaceBasePath;

  public DockerCodeqlPortAdapter(RunnerPort runnerPort, String workspaceBasePath) {
    this.runnerPort = runnerPort;
    this.workspaceBasePath = workspaceBasePath;
  }

  /**
   * CodeQL DB를 생성한다.
   *
   * @param req 생성 요청
   * @return DB 경로 및 실행 로그(stdout/stderr)
   */
  @Override
  public CreateDbResult createDatabase(CreateDbRequest req) {
    String workDir = ensureToolWorkDir(req.toolRunId(), "codeql");
    String dbDirOnHost = workDir + "/" + req.dbDirName();
    mkdirs(dbDirOnHost);

    // 컨테이너 내부 경로 규약
    String src = "/src";
    String work = "/work";

    String rel = trimSlashes(req.moduleRootRelPath());
    String moduleRootInContainer = rel.isBlank() ? src : (src + "/" + rel);

    String dbDirInContainer = work + "/" + req.dbDirName();

    // codeql database create <db> --language=java --source-root=<module> --command="<buildCommand>"
    String cmd = ""
        + "codeql database create " + shQuote(dbDirInContainer)
        + " --language=" + shQuote(req.language())
        + " --source-root=" + shQuote(moduleRootInContainer)
        + " --command=" + shQuote("bash -lc " + shQuote(req.buildCommand()))
        + " --overwrite";

    RunnerPort.ExecResult res = runDockerOrThrow(
        req.codeqlDockerImage(),
        req.sourceRootPathOnHost(),
        workDir,
        cmd,
        req.timeout(),
        "codeql database create failed"
    );

    return new CreateDbResult(dbDirOnHost, res.stdout(), res.stderr());
  }

  /**
   * CodeQL DB를 분석하여 SARIF를 생성한다.
   *
   * @param req 분석 요청
   * @return SARIF 경로 및 실행 로그(stdout/stderr)
   */
  @Override
  public RunQueriesResult runQueries(RunQueriesRequest req) {
    String dbDirOnHost = req.dbDirPathOnHost();
    String workDir = Path.of(dbDirOnHost).getParent().toString();
    String sarifOnHost = workDir + "/" + req.sarifFileName();
    mkdirs(workDir);

    String work = "/work";
    String dbDirInContainer = work + "/" + Path.of(dbDirOnHost).getFileName();
    String sarifInContainer = work + "/" + req.sarifFileName();

    // query packs: codeql/java-queries 등
    String packs = String.join(" ", req.queryPacks().stream().map(this::shQuote).toList());

    // codeql database analyze <db> <packs> --format=sarifv2.1.0 --output=<sarif>
    String cmd = ""
        + "codeql database analyze " + shQuote(dbDirInContainer)
        + " " + packs
        + " --format=sarifv2.1.0"
        + " --output=" + shQuote(sarifInContainer)
        + " --threads=0";

    RunnerPort.ExecResult res = runDockerOrThrow(
        req.codeqlDockerImage(),
        null,                // analyze는 src mount 없어도 됨
        workDir,
        cmd,
        req.timeout(),
        "codeql database analyze failed"
    );

    return new RunQueriesResult(sarifOnHost, res.stdout(), res.stderr());
  }

  /**
   * tool_run 단위의 작업 디렉토리를 만든다.
   *
   * @param toolRunId tool_run PK
   * @param toolName  하위 폴더명
   * @return 생성된 작업 디렉토리(호스트)
   */
  private String ensureToolWorkDir(Long toolRunId, String toolName) {
    String dir = workspaceBasePath + "/runs/toolRun-" + toolRunId + "/" + toolName;
    mkdirs(dir);
    return dir;
  }

  /**
   * 디렉토리를 생성한다(없으면 생성).
   *
   * @param dirPath 디렉토리 경로
   */
  private void mkdirs(String dirPath) {
    try {
      Files.createDirectories(Path.of(dirPath));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to create dir: " + dirPath, e);
    }
  }

  /**
   * docker run을 실행하고 실패하면 예외를 던진다.
   *
   * @param image Docker 이미지
   * @param sourceRootOnHost 소스 루트(옵션)
   * @param workDirOnHost 작업 디렉토리(호스트)
   * @param bashCmd 컨테이너에서 실행할 bash -lc 커맨드
   * @param timeout 실행 타임아웃
   * @param errorMessage 실패 메시지
   * @return 실행 결과(stdout/stderr/exitCode)
   */
  private RunnerPort.ExecResult runDockerOrThrow(
      String image,
      String sourceRootOnHost,
      String workDirOnHost,
      String bashCmd,
      Duration timeout,
      String errorMessage
  ) {
    String base = "docker run --rm";

    // /work는 반드시 RW로 마운트 (DB/SARIF 생성됨)
    String workMount = "-v " + shQuote(workDirOnHost) + ":/work";

    // /src는 read-only로 마운트해도 충분 (안전)
    String srcMount = (sourceRootOnHost == null)
        ? ""
        : (" -v " + shQuote(sourceRootOnHost) + ":/src:ro");

    String cmd = base
        + " " + workMount
        + srcMount
        + " -w /work"
        + " " + shQuote(image)
        + " bash -lc " + shQuote(bashCmd);

    RunnerPort.ExecResult res = runnerPort.run(new RunnerPort.ExecSpec(
        List.of("bash", "-lc", cmd),
        Map.of(),
        workspaceBasePath,
        timeout
    ));

    if (res.exitCode() != 0) {
      throw new IllegalStateException(
          errorMessage + " (exitCode=" + res.exitCode() + ")\n" + res.stderr()
      );
    }

    return res;
  }

  /**
   * 쉘 단일따옴표 quoting 처리.
   * 안전하게 ' 문자를 포함할 수 있도록 변환한다.
   *
   * @param s 입력 문자열
   * @return 안전한 단일따옴표 quoted 문자열
   */
  private String shQuote(String s) {
    // ' -> '"'"'
    return "'" + s.replace("'", "'\"'\"'") + "'";
  }

  /**
   * 앞뒤 슬래시를 제거한다.
   *
   * @param s 문자열
   * @return trim된 문자열
   */
  private String trimSlashes(String s) {
    if (s == null) return "";
    String r = s;
    while (r.startsWith("/")) r = r.substring(1);
    while (r.endsWith("/")) r = r.substring(0, r.length() - 1);
    return r;
  }
}
