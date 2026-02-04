package buaa.msasca.sca.core.application.usecase;

import java.io.InputStream;
import java.time.Instant;

public interface CreateProjectVersionFromZipUseCase {

    /**
     * ZIP 업로드 기반으로 project_version 생성 + zip 저장 + unzip + source_cache 생성까지 처리한다.
     *
     * @param req 요청
     * @return 결과
     */
    Response create(Request req);

    record Request(
        Long projectId,
        String versionLabel,
        String originalFilename,
        InputStream zipInputStream,
        Instant expiresAt
    ) {}

    record Response(
        Long projectVersionId,
        String sourceRootPath,
        String uploadZipPath
    ) {}
}