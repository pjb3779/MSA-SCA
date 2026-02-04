package buaa.msasca.sca.core.application.service;

import buaa.msasca.sca.core.application.support.WorkspacePathResolver;
import buaa.msasca.sca.core.application.usecase.CreateProjectVersionFromGitUseCase;
import buaa.msasca.sca.core.domain.enums.SourceType;
import buaa.msasca.sca.core.port.out.persistence.ProjectPort;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionPort;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionSourceCacheCommandPort;
import buaa.msasca.sca.core.port.out.tool.RunnerPort;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class CreateProjectVersionFromGitService implements CreateProjectVersionFromGitUseCase {

    private final ProjectPort projectPort;
    private final ProjectVersionPort projectVersionPort;
    private final ProjectVersionSourceCacheCommandPort cacheCommandPort;
    private final RunnerPort runnerPort;
    private final WorkspacePathResolver pathResolver;

    public CreateProjectVersionFromGitService(
        ProjectPort projectPort,
        ProjectVersionPort projectVersionPort,
        ProjectVersionSourceCacheCommandPort cacheCommandPort,
        RunnerPort runnerPort,
        WorkspacePathResolver pathResolver
    ) {
        this.projectPort = projectPort;
        this.projectVersionPort = projectVersionPort;
        this.cacheCommandPort = cacheCommandPort;
        this.runnerPort = runnerPort;
        this.pathResolver = pathResolver;
    }

    /**
     * Git URL 기반으로 project_version 생성 + clone + source_cache 생성까지 처리한다.
     *
     * @param req 요청
     * @return 결과
     */
    @Override
    public Response create(Request req) {
        projectPort.findById(req.projectId())
            .orElseThrow(() -> new IllegalArgumentException("project not found: " + req.projectId()));

        var pv = projectVersionPort.create(
            req.projectId(),
            req.versionLabel(),
            SourceType.GIT,
            req.gitUrl(),
            null,
            req.commitHash()
        );

        String sourceRoot = pathResolver.sourceRoot(req.projectId(), pv.id());
        ensureDir(sourceRoot);

        gitClone(req.gitUrl(), sourceRoot);

        if (req.commitHash() != null && !req.commitHash().isBlank()) {
        gitCheckout(sourceRoot, req.commitHash());
        }

        cacheCommandPort.createNewValid(pv.id(), sourceRoot, req.expiresAt());

        return new Response(pv.id(), sourceRoot);
    }

    /**
     * 디렉토리를 생성한다.
     *
     * @param dirPath 디렉토리 경로
     */
    private void ensureDir(String dirPath) {
        try {
        Files.createDirectories(Path.of(dirPath));
        } catch (Exception e) {
        throw new IllegalStateException("Failed to create dir: " + dirPath, e);
        }
    }

    /**
     * git clone을 실행한다.
     *
     * @param gitUrl git url
     * @param targetDir clone 대상 디렉토리
     */
    private void gitClone(String gitUrl, String targetDir) {
        RunnerPort.ExecResult res = runnerPort.run(new RunnerPort.ExecSpec(
        List.of("git", "clone", gitUrl, targetDir),
        Map.of(),
        "C:\\",
        Duration.ofMinutes(10)
    ));

    if (res.exitCode() != 0) {
        throw new IllegalStateException("git clone failed(exitCode=" + res.exitCode() + ")\nSTDOUT:\n" + res.stdout() + "\nSTDERR:\n" + res.stderr());
    }
    }
    // =========================
    // (Linux용) 기존 코드
    // =========================
    // private void gitClone(String gitUrl, String targetDir) {
    //     String cmd = "git clone " + shQuote(gitUrl) + " " + shQuote(targetDir);
    //     RunnerPort.ExecResult res = runnerPort.run(new RunnerPort.ExecSpec(
    //         List.of("bash", "-lc", cmd),
    //         Map.of(),
    //         "/",
    //         Duration.ofMinutes(10)
    //     ));
    //     if (res.exitCode() != 0) {
    //     throw new IllegalStateException("git clone failed(exitCode=" + res.exitCode() + ")\n" + res.stderr());
    //     }
    // }

    /**
     * git checkout을 실행한다.
     *
     * @param repoDir repo 디렉토리
     * @param ref commit hash 또는 태그/브랜치
     */
    private void gitCheckout(String repoDir, String ref) {
        RunnerPort.ExecResult res = runnerPort.run(new RunnerPort.ExecSpec(
        List.of("git", "-C", repoDir, "checkout", ref),
        Map.of(),
        "C:\\",
        Duration.ofMinutes(5)
    ));

    if (res.exitCode() != 0) {
        throw new IllegalStateException("git checkout failed(exitCode=" + res.exitCode() + ")\n" + res.stderr());
    }
    }
    // =========================
    // (Linux/WSL용) 기존 코드 - 유지하되 주석 처리
    // =========================
    // private void gitCheckout(String repoDir, String ref) {
    //     String cmd = "git -C " + shQuote(repoDir) + " checkout " + shQuote(ref);
    //     RunnerPort.ExecResult res = runnerPort.run(new RunnerPort.ExecSpec(
    //         List.of("bash", "-lc", cmd),
    //         Map.of(),
    //         "/",
    //         Duration.ofMinutes(5)
    //     ));
    //     if (res.exitCode() != 0) {
    //     throw new IllegalStateException("git checkout failed(exitCode=" + res.exitCode() + ")\n" + res.stderr());
    //     }
    // }

    /**
     * 단일따옴표 기반 quoting.
     *
     * @param s 문자열
     * @return quoted 문자열
     */
    private String shQuote(String s) {
        return "'" + s.replace("'", "'\"'\"'") + "'";
    }
}