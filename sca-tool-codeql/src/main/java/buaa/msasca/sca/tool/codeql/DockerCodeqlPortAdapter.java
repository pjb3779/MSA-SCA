package buaa.msasca.sca.tool.codeql;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import buaa.msasca.sca.core.port.out.tool.CodeqlPort;
import buaa.msasca.sca.core.port.out.tool.RunnerPort;

public class DockerCodeqlPortAdapter implements CodeqlPort {

    private static final Logger log = LoggerFactory.getLogger(DockerCodeqlPortAdapter.class);

    private final RunnerPort runnerPort;
    private final String workspaceBasePath;

    public DockerCodeqlPortAdapter(RunnerPort runnerPort, String workspaceBasePath) {
        this.runnerPort = runnerPort;
        this.workspaceBasePath = workspaceBasePath;
    }

    /**
     * CodeQL DB를 생성한다(docker 기반).
     *
     * @param req 생성 요청
     * @return DB 경로/로그
     */
    @Override
    public CodeqlPort.CreateDbResult createDatabase(CodeqlPort.CreateDbRequest req) {
        // toolRun 단위 작업 디렉토리
        String workDir = ensureToolWorkDir(req.toolRunId(), "codeql");

        // 호스트에서 DB 디렉토리 경로
        String dbDirOnHost = workDir + "/" + req.dbDirName();
        ensureEmptyDir(dbDirOnHost); // 항상 "비어 있는" 디렉토리 보장

        // 컨테이너 내부 경로 규약
        String srcIn = "/src";
        String workIn = "/work";

        // moduleRootRelPath == "gateway" 같은 상대 경로
        String moduleRel = trimSlashes(req.moduleRootRelPath());
        String moduleRootIn = moduleRel.isBlank()
            ? srcIn
            : (srcIn + "/" + moduleRel);

        // 컨테이너 안에서의 DB 디렉토리
        String dbDirIn = workIn + "/" + req.dbDirName();

        // codeql database create <db> --language=java --source-root=<module> --overwrite
        // (build command는 CodeQL autobuild에 맡김)
        String cmd = ""
            + "codeql database create " + dbDirIn
            + " --language=" + req.language()
            + " --source-root=" + moduleRootIn
            + " --overwrite";

        RunnerPort.ExecResult res = runDockerOrThrow(
            req.codeqlDockerImage(),
            req.sourceRootPathOnHost(), // C:/msasca/projects/.../source
            workDir,
            cmd,
            req.timeout(),
            "codeql database create failed"
        );

        log.info(
            "[CodeQL] createDatabase ok toolRunId={} serviceModuleId={} dbDir={}",
            req.toolRunId(), req.serviceModuleId(), dbDirOnHost
        );

        return new CodeqlPort.CreateDbResult(dbDirOnHost, res.stdout(), res.stderr());
    }

    /**
     * CodeQL DB를 분석하여 SARIF를 생성한다(docker 기반).
     *
     * @param req 분석 요청
     * @return SARIF 경로/로그
     */
    @Override
    public CodeqlPort.RunQueriesResult runQueries(CodeqlPort.RunQueriesRequest req) {
        String dbDirOnHost = req.dbDirPathOnHost();
        String workDir = Path.of(dbDirOnHost).getParent().toString();
        mkdirs(workDir);

        String sarifOnHost = workDir + "/" + req.sarifFileName();

        String workIn = "/work";
        String dbDirIn = workIn + "/" + Path.of(dbDirOnHost).getFileName();
        String sarifIn = workIn + "/" + req.sarifFileName();

        String packs = String.join(" ", req.queryPacks());

        String cmd = ""
            + "codeql database analyze " + dbDirIn
            + " " + packs
            + " --search-path /opt/codeql-home/codeql/qlpacks"
            + " --format=sarifv2.1.0"
            + " --output=" + sarifIn
            + " --threads=0";

        RunnerPort.ExecResult res = runDockerOrThrow(
            req.codeqlDockerImage(),
            null, // analyze는 src mount 없어도 됨
            workDir,
            cmd,
            req.timeout(),
            "codeql database analyze failed"
        );

        log.info(
            "[CodeQL] runQueries ok toolRunId={} serviceModuleId={} sarif={}",
            req.toolRunId(), req.serviceModuleId(), sarifOnHost
        );

        return new CodeqlPort.RunQueriesResult(sarifOnHost, res.stdout(), res.stderr());
    }

    // =====================================================================
    // 디렉토리/워크스페이스 헬퍼
    // =====================================================================

    /**
     * tool_run 단위 작업 디렉토리를 보장한다.
     *
     * @param toolRunId tool_run ID
     * @param toolName  하위 디렉토리명
     * @return 작업 디렉토리(호스트)
     */
    private String ensureToolWorkDir(Long toolRunId, String toolName) {
        String dir = workspaceBasePath + "/runs/toolRun-" + toolRunId + "/" + toolName;
        mkdirs(dir);
        return dir;
    }

    /**
     * 디렉토리를 생성한다(존재하면 그대로).
     */
    private void mkdirs(String dirPath) {
        try {
            Files.createDirectories(Path.of(dirPath));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create dir: " + dirPath, e);
        }
    }

    /**
     * 디렉토리가 존재하면 내부를 모두 지우고, 비어 있는 상태로 다시 만든다.
     */
    private void ensureEmptyDir(String dirPath) {
        try {
            Path dir = Path.of(dirPath);
            if (Files.exists(dir)) {
                // 하위 파일/디렉토리 모두 삭제 (깊은 경로부터)
                Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to delete " + p, e);
                        }
                    });
            }
            Files.createDirectories(dir);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to ensure empty dir: " + dirPath, e);
        }
    }

    // =====================================================================
    // docker 실행 헬퍼
    // =====================================================================

    /**
     * docker run을 실행하고 실패하면 예외를 던진다.
     *
     * @param image            Docker 이미지
     * @param sourceRootOnHost 소스 루트(옵션, C:/.../source)
     * @param workDirOnHost    작업 디렉토리(호스트, C:/.../runs/toolRun-x/codeql)
     * @param bashCmd          컨테이너에서 실행할 bash -lc 커맨드
     * @param timeout          실행 타임아웃
     * @param errorMessage     실패 메시지 prefix
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
        // 필수 값 검증
        if (image == null || image.isBlank()) {
            throw new IllegalStateException(errorMessage + ": docker image is null/blank");
        }
        if (bashCmd == null || bashCmd.isBlank()) {
            throw new IllegalStateException(errorMessage + ": bash command is null/blank");
        }
        if (workDirOnHost == null || workDirOnHost.isBlank()) {
            throw new IllegalStateException(errorMessage + ": workDirOnHost is null/blank");
        }

        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("--rm");

        // /work는 DB/SARIF 생성되므로 RW 필요
        cmd.add("-v");
        cmd.add(workDirOnHost + ":/work");

        // /src는 읽기 전용으로 마운트(옵션)
        if (sourceRootOnHost != null && !sourceRootOnHost.isBlank()) {
            cmd.add("-v");
            cmd.add(sourceRootOnHost + ":/src");
        }

        cmd.add("-w");
        cmd.add("/work");
        cmd.add(image);
        cmd.add("bash");
        cmd.add("-lc");
        cmd.add(bashCmd);

        log.info("[CodeQL] docker cmd = {}", String.join(" ", cmd));

        RunnerPort.ExecResult res = runnerPort.run(
            new RunnerPort.ExecSpec(
                cmd,
                Map.of(),
                workspaceBasePath,
                timeout
            )
        );

        if (res.exitCode() != 0) {
            String msg = errorMessage
                + " (exitCode=" + res.exitCode() + ")"
                + System.lineSeparator()
                + "----- STDOUT -----" + System.lineSeparator()
                + res.stdout()
                + System.lineSeparator()
                + "----- STDERR -----" + System.lineSeparator()
                + res.stderr();

            log.error("[CodeQL] docker failed image={} workDir={} exitCode={}\n{}",
                image, workDirOnHost, res.exitCode(), msg);

            throw new IllegalStateException(msg);
        }

        return res;
    }

    // =====================================================================
    // 문자열 유틸
    // =====================================================================

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