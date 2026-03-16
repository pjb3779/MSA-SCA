package buaa.msasca.sca.core.application.service;

import buaa.msasca.sca.core.application.support.MscanConfigAutoBuilder;
import buaa.msasca.sca.core.application.support.WorkspacePathResolver;
import buaa.msasca.sca.core.application.usecase.CreateProjectVersionFromGitUseCase;
import buaa.msasca.sca.core.domain.enums.SourceType;
import buaa.msasca.sca.core.port.out.persistence.ProjectPort;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionPort;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionSourceCacheCommandPort;
import buaa.msasca.sca.core.port.out.tool.RunnerPort;

import com.fasterxml.jackson.databind.node.ObjectNode;

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

    private final EnqueueAnalysisRunOnSourceReadyService enqueueService;

    public CreateProjectVersionFromGitService(
        ProjectPort projectPort,
        ProjectVersionPort projectVersionPort,
        ProjectVersionSourceCacheCommandPort cacheCommandPort,
        RunnerPort runnerPort,
        WorkspacePathResolver pathResolver,
        EnqueueAnalysisRunOnSourceReadyService enqueueService
    ) {
        this.projectPort = projectPort;
        this.projectVersionPort = projectVersionPort;
        this.cacheCommandPort = cacheCommandPort;
        this.runnerPort = runnerPort;
        this.pathResolver = pathResolver;
        this.enqueueService = enqueueService;
    }

    /**
     * Git URL 기반으로 project_version 생성 + clone + source_cache 생성까지 처리한다.
     * + (패턴 B) source_cache valid 이후 analysis_run 자동 생성
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

        // source cache 생성
        cacheCommandPort.createNewValid(pv.id(), sourceRoot, req.expiresAt());

        // config_json 자동 생성 + analysis_run 자동 생성
        Long runId = null;
        String autoErr = null;
        try {
            ObjectNode autoCfg = MscanConfigAutoBuilder.buildDefaultConfig(pv.id(), sourceRoot);
            var enq = enqueueService.enqueueIfAbsent(pv.id(), autoCfg, "system");

            runId = enq.analysisRunId();
            autoErr = enq.errorMessage();

            // skipped인 경우도 응답에 표시
            if (autoErr == null && enq.skipped()) {
                autoErr = "active run already exists (skipped)";
            }
        } catch (Exception e) {
            autoErr = (e.getMessage() == null) ? e.toString() : e.getMessage();
        }

        return new Response(pv.id(), sourceRoot, runId, autoErr);
    }

    /**
     * 디렉토리를 생성한다.
     */
    private void ensureDir(String dirPath) {
        try {
            Files.createDirectories(Path.of(dirPath));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create dir: " + dirPath, e);
        }
    }

    /**
     * git clone을 실행한다. (Windows 기본)
     */
    private void gitClone(String gitUrl, String targetDir) {
        RunnerPort.ExecResult res = runnerPort.run(new RunnerPort.ExecSpec(
            List.of("git", "clone", gitUrl, targetDir),
            Map.of(),
            "C:\\",
            Duration.ofMinutes(10)
        ));

        if (res.exitCode() != 0) {
            throw new IllegalStateException(
                "git clone failed(exitCode=" + res.exitCode() + ")\nSTDOUT:\n" + res.stdout() + "\nSTDERR:\n" + res.stderr()
            );
        }
    }

    /**
     * git checkout을 실행한다. (Windows 기본)
     */
    private void gitCheckout(String repoDir, String ref) {
        RunnerPort.ExecResult res = runnerPort.run(new RunnerPort.ExecSpec(
            List.of("git", "-C", repoDir, "checkout", ref),
            Map.of(),
            "C:\\",
            Duration.ofMinutes(5)
        ));

        if (res.exitCode() != 0) {
            throw new IllegalStateException(
                "git checkout failed(exitCode=" + res.exitCode() + ")\nSTDOUT:\n" + res.stdout() + "\nSTDERR:\n" + res.stderr()
            );
        }
    }

    // (Linux/WSL용) 필요하면 아래 방식으로 교체 가능
    @SuppressWarnings("unused")
    private String shQuote(String s) {
        return "'" + s.replace("'", "'\"'\"'") + "'";
    }
}