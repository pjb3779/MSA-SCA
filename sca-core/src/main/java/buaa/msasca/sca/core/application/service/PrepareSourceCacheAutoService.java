package buaa.msasca.sca.core.application.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import buaa.msasca.sca.core.domain.model.ProjectVersionSourceCache;
import buaa.msasca.sca.core.domain.model.ProjectVersionView;
import buaa.msasca.sca.core.port.in.PrepareSourceCacheAutoUseCase;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionPort;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionSourceCacheCommandPort;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionSourceCachePort;
import buaa.msasca.sca.core.port.out.tool.RunnerPort;

public class PrepareSourceCacheAutoService implements PrepareSourceCacheAutoUseCase {
    
    private final ProjectVersionPort projectVersionPort;
    private final ProjectVersionSourceCachePort cachePort;
    private final ProjectVersionSourceCacheCommandPort cacheCommandPort;
    private final RunnerPort runnerPort;

    private final String workspaceBasePath;

    public PrepareSourceCacheAutoService(
        ProjectVersionPort projectVersionPort,
        ProjectVersionSourceCachePort cachePort,
        ProjectVersionSourceCacheCommandPort cacheCommandPort,
        RunnerPort runnerPort,
        String workspaceBasePath
    ) {
        this.projectVersionPort = projectVersionPort;
        this.cachePort = cachePort;
        this.cacheCommandPort = cacheCommandPort;
        this.runnerPort = runnerPort;
        this.workspaceBasePath = workspaceBasePath;
    }

    /**
     * 소스 캐시를 자동 준비한다.
     * - forceRefresh=false 이고 valid cache가 이미 있으면 그대로 반환한다.
     * - ZIP: uploadFilePath를 unzip 해서 workspace에 푼다.
     * - GIT: sourceUrl을 clone 하고 commit/tag checkout 한다(가능하면).
     */
    @Override
    public ProjectVersionSourceCache handle(Command command) {
        ProjectVersionView pv = projectVersionPort.findById(command.projectVersionId())
            .orElseThrow(() -> new IllegalArgumentException("project_version not found: " + command.projectVersionId()));

        if (!command.forceRefresh()) {
        var existing = cachePort.findValidByProjectVersionId(pv.id());
        if (existing.isPresent()) return existing.get();
        }

        String preparedPath = switch (pv.sourceType()) {
        case ZIP -> prepareZip(pv);
        case GIT -> prepareGit(pv);
        case OTHER -> prepareOther(pv);
        };

        Instant expiresAt = command.expiresAt();
        return cacheCommandPort.createNewValid(pv.id(), preparedPath, expiresAt);
    }

    /** ZIP 소스를 workspace에 압축해제한다. */
    private String prepareZip(ProjectVersionView pv) {
        if (pv.uploadFilePath() == null || pv.uploadFilePath().isBlank()) {
        throw new IllegalStateException("ZIP source requires uploadFilePath. projectVersionId=" + pv.id());
        }

        String dir = newWorkspaceDir("zip", pv.id());
        mkdirs(dir);

        // unzip -q <zip> -d <dir>
        String cmd = "unzip -q " + shQuote(pv.uploadFilePath()) + " -d " + shQuote(dir);
        runOrThrow(dir, cmd, "unzip failed");

        return dir;
    }

    /** GIT 소스를 workspace에 clone/checkout 한다. */
    private String prepareGit(ProjectVersionView pv) {
        if (pv.sourceUrl() == null || pv.sourceUrl().isBlank()) {
        throw new IllegalStateException("GIT source requires sourceUrl. projectVersionId=" + pv.id());
        }

        String dir = newWorkspaceDir("git", pv.id());
        mkdirs(dir);

        // git clone <url> <dir>
        String cloneCmd = "git clone " + shQuote(pv.sourceUrl()) + " " + shQuote(dir);
        runOrThrow(workspaceBasePath, cloneCmd, "git clone failed");

        // checkout 우선순위: commit_hash > version_label(있으면)
        if (pv.vcsCommitHash() != null && !pv.vcsCommitHash().isBlank()) {
        String checkout = "git -C " + shQuote(dir) + " checkout " + shQuote(pv.vcsCommitHash());
        runOrThrow(workspaceBasePath, checkout, "git checkout(commit) failed");
        } else if (pv.versionLabel() != null && !pv.versionLabel().isBlank()) {
        String checkout = "git -C " + shQuote(dir) + " checkout " + shQuote(pv.versionLabel());
        runOrThrow(workspaceBasePath, checkout, "git checkout(versionLabel) failed");
        }

        return dir;
    }

    /**
     * OTHER 소스 처리.
     * 현재는 "로컬 경로"를 sourceUrl로 받는 경우만 지원한다.
     */
    private String prepareOther(ProjectVersionView pv) {
        if (pv.sourceUrl() == null || pv.sourceUrl().isBlank()) {
        throw new IllegalStateException("OTHER source requires sourceUrl(local path). projectVersionId=" + pv.id());
        }

        String url = pv.sourceUrl();
        if (url.startsWith("file://")) {
        return url.substring("file://".length());
        }
        if (url.startsWith("/")) {
        return url;
        }
        throw new IllegalStateException("Unsupported OTHER sourceUrl. Expect local path or file://. sourceUrl=" + url);
    }

    /** workspace 하위에 고유 디렉토리를 만든다. */
    private String newWorkspaceDir(String prefix, Long projectVersionId) {
        long ts = System.currentTimeMillis();
        return workspaceBasePath + "/pv-" + projectVersionId + "-" + prefix + "-" + ts;
    }

    /** 디렉토리를 생성한다(없으면 생성). */
    private void mkdirs(String dir) {
        try {
        Files.createDirectories(Path.of(dir));
        } catch (Exception e) {
        throw new IllegalStateException("Failed to create dir: " + dir, e);
        }
    }

    /**
     * bash -lc 로 명령을 실행하고, 실패하면 예외를 던진다.
     * @param workDir 실행 작업 디렉토리
     * @param bashCmd bash -lc 로 실행할 문자열
     * @param errorMessage 실패 시 메시지
     */
    private void runOrThrow(String workDir, String bashCmd, String errorMessage) {
        RunnerPort.ExecResult res = runnerPort.run(new RunnerPort.ExecSpec(
            List.of("bash", "-lc", bashCmd),
            Map.of(),
            workDir,
            java.time.Duration.ofMinutes(30)
        ));

        if (res.exitCode() != 0) {
        throw new IllegalStateException(errorMessage + " (exitCode=" + res.exitCode() + ")\n" + res.stderr());
        }
    }

    /**
     * bash 안전 인용(단일따옴표 기반).
     * @param s 문자열
     * @return 쉘에서 안전한 형태의 단일따옴표 quoting
     */
    private String shQuote(String s) {
        // ' -> '"'"'
        return "'" + s.replace("'", "'\"'\"'") + "'";
    }
}