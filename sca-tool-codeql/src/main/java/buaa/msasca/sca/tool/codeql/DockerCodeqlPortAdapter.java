package buaa.msasca.sca.tool.codeql;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import buaa.msasca.sca.core.application.build.AutobuildErrorCatalog;
import buaa.msasca.sca.core.application.build.ParentPomFinder;
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
     * 모듈 단위로 먼저 시도하고, 에러 패턴에 해당하면 프로젝트 루트 폴백을 재시도한다.
     *
     * @param req 생성 요청
     * @return DB 경로/로그
     */
    @Override
    public CodeqlPort.CreateDbResult createDatabase(CodeqlPort.CreateDbRequest req) {
        String workDir = ensureToolWorkDir(req.toolRunId(), "codeql");
        String dbDirOnHost = workDir + "/" + req.dbDirName();
        ensureEmptyDir(dbDirOnHost);

        String srcIn = "/src";
        String workIn = "/work";
        String dbDirIn = workIn + "/" + req.dbDirName();
        String moduleRel = trimSlashes(req.moduleRootRelPath());
        String sourceRootIn = moduleRel.isBlank() ? srcIn : (srcIn + "/" + moduleRel);
        String buildCmd = req.buildCommand();
        boolean useCommand = buildCmd != null && !buildCmd.isBlank();

        String cmd = ""
            + "codeql database create " + dbDirIn
            + " --language=" + req.language()
            + " --source-root=" + sourceRootIn
            + " --overwrite";
        if (useCommand) {
            cmd = cmd + " --command='" + buildCmd.replace("'", "'\\''") + "'";
        }

        RunnerPort.ExecResult res = runDocker(
            req.codeqlDockerImage(),
            req.sourceRootPathOnHost(),
            workDir,
            cmd,
            req.timeout()
        );

        if (res.exitCode() == 0) {
            log.info("[CodeQL] createDatabase ok toolRunId={} serviceModuleId={} dbDir={}",
                req.toolRunId(), req.serviceModuleId(), dbDirOnHost);
            return new CodeqlPort.CreateDbResult(dbDirOnHost, res.stdout(), res.stderr());
        }

        String combined = res.stdout() + "\n" + res.stderr();

        // 카탈로그: 재시도 (네트워크 등 일시적 오류)
        int retryCount = AutobuildErrorCatalog.getRetryCount(combined);
        for (int attempt = 1; attempt <= retryCount; attempt++) {
            log.warn("[CodeQL] createDatabase failed, retry {}/{} toolRunId={} serviceModuleId={}",
                attempt, retryCount, req.toolRunId(), req.serviceModuleId());
            try {
                Thread.sleep(AutobuildErrorCatalog.RETRY_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted during retry", e);
            }
            ensureEmptyDir(dbDirOnHost);
            res = runDocker(req.codeqlDockerImage(), req.sourceRootPathOnHost(), workDir, cmd, req.timeout());
            if (res.exitCode() == 0) {
                return new CodeqlPort.CreateDbResult(dbDirOnHost, res.stdout(), res.stderr());
            }
            combined = res.stdout() + "\n" + res.stderr();
        }

        // 카탈로그: 폴백 (parent POM 등 구조적 오류)
        if (AutobuildErrorCatalog.requiresFallback(combined, AutobuildErrorCatalog.FallbackStrategy.USE_PROJECT_ROOT)
            && req.fallbackBuildCommand() != null && !req.fallbackBuildCommand().isBlank()) {

            log.warn("[CodeQL] createDatabase failed (parent POM), searching parent in source tree then retrying toolRunId={} serviceModuleId={}",
                req.toolRunId(), req.serviceModuleId());

            String mavenSkips = "-Dfindbugs.skip -Dcheckstyle.skip -Dpmd.skip=true -Dspotbugs.skip "
                + "-Denforcer.skip -Dmaven.javadoc.skip -Dlicense.skip=true -Drat.skip=true -Dspotless.check.skip=true";

            // 1) 소스 트리에서 parent POM 검색 후 install (같은 repo에 parent 모듈이 있는 경우)
            Path sourceRootPath = Path.of(req.sourceRootPathOnHost());
            Path failingPomPath = moduleRel.isBlank()
                ? sourceRootPath.resolve("pom.xml")
                : sourceRootPath.resolve(moduleRel).resolve("pom.xml");
            ParentPomFinder.findParentPomPath(sourceRootPath, failingPomPath).ifPresent(parentRel -> {
                String parentPathInContainer = "/src/" + parentRel.toString().replace('\\', '/');
                String installParentCmd = "mvn -f " + parentPathInContainer + " install -N -DskipTests " + mavenSkips;
                RunnerPort.ExecResult parentRes = runDocker(
                    req.codeqlDockerImage(),
                    req.sourceRootPathOnHost(),
                    workDir,
                    installParentCmd,
                    req.timeout()
                );
                if (parentRes.exitCode() == 0) {
                    log.info("[CodeQL] parent POM installed toolRunId={} serviceModuleId={} parentPath={}",
                        req.toolRunId(), req.serviceModuleId(), parentPathInContainer);
                } else {
                    log.warn("[CodeQL] parent POM install failed (continuing) toolRunId={} serviceModuleId={} path={}",
                        req.toolRunId(), req.serviceModuleId(), parentPathInContainer);
                }
            });

            // 2) root POM install (root가 parent인 경우)
            String installRootCmd = "mvn -f /src/pom.xml install -N -DskipTests " + mavenSkips;
            RunnerPort.ExecResult installRes = runDocker(
                req.codeqlDockerImage(),
                req.sourceRootPathOnHost(),
                workDir,
                installRootCmd,
                req.timeout()
            );
            if (installRes.exitCode() != 0) {
                log.warn("[CodeQL] mvn install -N failed (continuing anyway) toolRunId={} serviceModuleId={}",
                    req.toolRunId(), req.serviceModuleId());
            } else {
                log.info("[CodeQL] mvn install -N ok, root POM installed toolRunId={} serviceModuleId={}",
                    req.toolRunId(), req.serviceModuleId());
            }

            ensureEmptyDir(dbDirOnHost);
            String fallbackCmd = ""
                + "codeql database create " + dbDirIn
                + " --language=" + req.language()
                + " --source-root=" + srcIn
                + " --overwrite"
                + " --command='" + req.fallbackBuildCommand().replace("'", "'\\''") + "'";

            res = runDocker(
                req.codeqlDockerImage(),
                req.sourceRootPathOnHost(),
                workDir,
                fallbackCmd,
                req.timeout()
            );

            if (res.exitCode() == 0) {
                return new CodeqlPort.CreateDbResult(dbDirOnHost, res.stdout(), res.stderr());
            }

            String fallbackCombined = res.stdout() + "\n" + res.stderr();

            // 프로젝트 루트도 parent POM 실패 시: --build-mode none (빌드 없이 DB 생성, CodeQL 2.16.5+)
            if ("java".equalsIgnoreCase(req.language())
                && AutobuildErrorCatalog.requiresFallback(fallbackCombined, AutobuildErrorCatalog.FallbackStrategy.USE_PROJECT_ROOT)) {

                // 모듈 경로, 프로젝트 루트 순으로 source-root 시도 (CodeQL "did not detect any code" 완화)
                String[] sourceRootCandidates = { sourceRootIn, srcIn };
                for (String candidateRoot : sourceRootCandidates) {
                    log.warn("[CodeQL] project root fallback failed (parent POM), retrying with --build-mode none source-root={} toolRunId={} serviceModuleId={}",
                        candidateRoot, req.toolRunId(), req.serviceModuleId());

                    ensureEmptyDir(dbDirOnHost);
                    String noBuildCmd = ""
                        + "codeql database create " + dbDirIn
                        + " --language=java"
                        + " --source-root=" + candidateRoot
                        + " --build-mode=none"
                        + " --overwrite";

                    res = runDocker(
                        req.codeqlDockerImage(),
                        req.sourceRootPathOnHost(),
                        workDir,
                        noBuildCmd,
                        req.timeout()
                    );

                    if (res.exitCode() == 0) {
                        log.info("[CodeQL] createDatabase ok (build-mode=none) toolRunId={} serviceModuleId={} dbDir={} source-root={}",
                            req.toolRunId(), req.serviceModuleId(), dbDirOnHost, candidateRoot);
                        return new CodeqlPort.CreateDbResult(dbDirOnHost, res.stdout(), res.stderr());
                    }
                }
            }

            throw throwWithResult(res, "codeql database create failed (after project root fallback)");
        }

        throw throwWithResult(res, "codeql database create failed");
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

        StringBuilder cmd = new StringBuilder()
            .append("codeql database analyze ").append(dbDirIn)
            .append(" ").append(packs)
            .append(" --search-path /opt/codeql-home/codeql/qlpacks")
            .append(" --format=sarifv2.1.0")
            .append(" --output=").append(sarifIn)
            .append(" --threads=0");
        if (req.analyzeRam() != null && !req.analyzeRam().isBlank()) {
            cmd.append(" --ram=").append(req.analyzeRam().trim());
        }

        RunnerPort.ExecResult res = runDockerOrThrow(
            req.codeqlDockerImage(),
            null, // analyze는 src mount 없어도 됨
            workDir,
            cmd.toString(),
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
     * Maven settings.xml에 Spring 저장소(releases, milestones, snapshots)를 포함하여 생성한다.
     * spring-cloud-dataflow-build 등 Spring 부모 POM resolve를 위해 필요.
     * workDir/maven-repo에 두면 /root/.m2로 마운트 시 Maven이 자동 사용.
     */
    private void ensureMavenSettingsWithSpringRepos(String mavenRepoDir) {
        Path settingsPath = Path.of(mavenRepoDir, "settings.xml");
        if (Files.exists(settingsPath)) {
            return;
        }
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<settings xmlns=\"http://maven.apache.org/SETTINGS/1.2.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
            + "xsi:schemaLocation=\"http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd\">\n"
            + "  <profiles>\n"
            + "    <profile>\n"
            + "      <id>spring-repos</id>\n"
            + "      <repositories>\n"
            + "        <repository><id>central</id><url>https://repo.maven.apache.org/maven2</url><snapshots><enabled>false</enabled></snapshots></repository>\n"
            + "        <repository><id>spring-releases</id><url>https://repo.spring.io/release</url><snapshots><enabled>false</enabled></snapshots></repository>\n"
            + "        <repository><id>spring-milestones</id><url>https://repo.spring.io/milestone</url><snapshots><enabled>false</enabled></snapshots></repository>\n"
            + "        <repository><id>spring-snapshots</id><url>https://repo.spring.io/snapshot</url><snapshots><enabled>true</enabled></snapshots></repository>\n"
            + "      </repositories>\n"
            + "      <pluginRepositories>\n"
            + "        <pluginRepository><id>central</id><url>https://repo.maven.apache.org/maven2</url><snapshots><enabled>false</enabled></snapshots></pluginRepository>\n"
            + "        <pluginRepository><id>spring-releases</id><url>https://repo.spring.io/release</url><snapshots><enabled>false</enabled></snapshots></pluginRepository>\n"
            + "        <pluginRepository><id>spring-milestones</id><url>https://repo.spring.io/milestone</url><snapshots><enabled>false</enabled></snapshots></pluginRepository>\n"
            + "        <pluginRepository><id>spring-snapshots</id><url>https://repo.spring.io/snapshot</url><snapshots><enabled>true</enabled></snapshots></pluginRepository>\n"
            + "      </pluginRepositories>\n"
            + "    </profile>\n"
            + "  </profiles>\n"
            + "  <activeProfiles><activeProfile>spring-repos</activeProfile></activeProfiles>\n"
            + "</settings>";
        try {
            Files.writeString(settingsPath, xml, StandardCharsets.UTF_8);
            log.info("[CodeQL] created Maven settings.xml with Spring repos at {}", settingsPath);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write settings.xml: " + settingsPath, e);
        }
    }

    /**
     * 디렉토리가 존재하면 내부를 모두 지우고, 비어 있는 상태로 다시 만든다.
     * Windows에서 Files.walk() Stream이 열린 상태로 삭제하면 DirectoryNotEmptyException이 발생하므로,
     * 경로를 리스트로 모은 뒤 Stream을 닫고 삭제한다.
     */
    private void ensureEmptyDir(String dirPath) {
        try {
            Path dir = Path.of(dirPath);
            if (Files.exists(dir)) {
                List<Path> pathsToDelete;
                try (Stream<Path> walk = Files.walk(dir)) {
                    pathsToDelete = walk.sorted(Comparator.reverseOrder()).toList();
                }
                for (Path p : pathsToDelete) {
                    try {
                        Files.delete(p);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to delete " + p, e);
                    }
                }
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
     * docker run을 실행한다. 실패해도 예외를 던지지 않고 결과만 반환한다.
     */
    private RunnerPort.ExecResult runDocker(
        String image,
        String sourceRootOnHost,
        String workDirOnHost,
        String bashCmd,
        Duration timeout
    ) {
        validateDockerArgs(image, bashCmd, workDirOnHost, null);
        List<String> cmd = buildDockerCmd(image, sourceRootOnHost, workDirOnHost, bashCmd);
        log.info("[CodeQL] docker cmd = {}", String.join(" ", cmd));
        return runnerPort.run(
            new RunnerPort.ExecSpec(cmd, Map.of(), workspaceBasePath, timeout)
        );
    }

    /**
     * docker run을 실행하고 실패하면 예외를 던진다.
     */
    private RunnerPort.ExecResult runDockerOrThrow(
        String image,
        String sourceRootOnHost,
        String workDirOnHost,
        String bashCmd,
        Duration timeout,
        String errorMessage
    ) {
        validateDockerArgs(image, bashCmd, workDirOnHost, errorMessage);
        RunnerPort.ExecResult res = runDocker(image, sourceRootOnHost, workDirOnHost, bashCmd, timeout);
        if (res.exitCode() != 0) {
            throw throwWithResult(res, errorMessage);
        }
        return res;
    }

    private void validateDockerArgs(String image, String bashCmd, String workDirOnHost, String errorPrefix) {
        String prefix = (errorPrefix != null ? errorPrefix + ": " : "");
        if (image == null || image.isBlank()) {
            throw new IllegalStateException(prefix + "docker image is null/blank");
        }
        if (bashCmd == null || bashCmd.isBlank()) {
            throw new IllegalStateException(prefix + "bash command is null/blank");
        }
        if (workDirOnHost == null || workDirOnHost.isBlank()) {
            throw new IllegalStateException(prefix + "workDirOnHost is null/blank");
        }
    }

    private List<String> buildDockerCmd(
        String image,
        String sourceRootOnHost,
        String workDirOnHost,
        String bashCmd
    ) {
        return buildDockerCmd(image, sourceRootOnHost, workDirOnHost, bashCmd, true);
    }

    /**
     * Maven 로컬 저장소 마운트 포함 여부.
     * parent POM install 후 CodeQL에서 동일 repo를 사용하려면 툴런별 maven-repo 공유 필요.
     */
    private List<String> buildDockerCmd(
        String image,
        String sourceRootOnHost,
        String workDirOnHost,
        String bashCmd,
        boolean mountMavenRepo
    ) {
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("--rm");
        cmd.add("-v");
        cmd.add(workDirOnHost + ":/work");
        if (sourceRootOnHost != null && !sourceRootOnHost.isBlank()) {
            cmd.add("-v");
            cmd.add(sourceRootOnHost + ":/src");
        }
        if (mountMavenRepo && sourceRootOnHost != null && !sourceRootOnHost.isBlank()) {
            String mavenRepoDir = workDirOnHost + "/maven-repo";
            mkdirs(mavenRepoDir);
            ensureMavenSettingsWithSpringRepos(mavenRepoDir);
            cmd.add("-v");
            cmd.add(mavenRepoDir + ":/root/.m2");
        }
        cmd.add("-w");
        cmd.add("/work");
        cmd.add(image);
        cmd.add("bash");
        cmd.add("-lc");
        cmd.add(bashCmd);
        return cmd;
    }

    private IllegalStateException throwWithResult(RunnerPort.ExecResult res, String errorMessage) {
        String msg = errorMessage
            + " (exitCode=" + res.exitCode() + ")"
            + System.lineSeparator()
            + "----- STDOUT -----" + System.lineSeparator()
            + res.stdout()
            + System.lineSeparator()
            + "----- STDERR -----" + System.lineSeparator()
            + res.stderr();
        return new IllegalStateException(msg);
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