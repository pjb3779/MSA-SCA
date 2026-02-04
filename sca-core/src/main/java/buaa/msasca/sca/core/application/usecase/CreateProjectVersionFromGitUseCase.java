package buaa.msasca.sca.core.application.usecase;

import java.time.Instant;

public interface CreateProjectVersionFromGitUseCase {

    /**
     * Git URL로 project_version을 생성하고 로컬(/msasca)에 clone 후 source cache를 생성한다.
     *
     * @param req 요청
     * @return 결과
     */
    Response create(Request req);

    record Request(
        Long projectId,
        String versionLabel,
        String gitUrl,
        String commitHash,
        Instant expiresAt
    ) {}

    record Response(
        Long projectVersionId,
        String sourceRootPath
    ) {}
}