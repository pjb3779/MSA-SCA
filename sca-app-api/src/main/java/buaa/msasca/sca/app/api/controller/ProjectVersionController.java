package buaa.msasca.sca.app.api.controller;

import buaa.msasca.sca.core.application.usecase.CreateProjectVersionFromGitUseCase;
import buaa.msasca.sca.core.application.usecase.CreateProjectVersionFromZipUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Instant;

@RestController
@RequestMapping("/api/projects")
public class ProjectVersionController {

    private final CreateProjectVersionFromGitUseCase gitUseCase;
    private final CreateProjectVersionFromZipUseCase zipUseCase;

    public ProjectVersionController(
        CreateProjectVersionFromGitUseCase gitUseCase,
        CreateProjectVersionFromZipUseCase zipUseCase
    ) {
        this.gitUseCase = gitUseCase;
        this.zipUseCase = zipUseCase;
    }

    public record CreateFromGitRequest(
        @NotBlank String versionLabel,
        @NotBlank String gitUrl,
        String commitHash,
        String expiresAt
    ) {}

    public record CreateFromGitResponse(
        Long projectVersionId,
        String sourceRootPath
    ) {}

    public record CreateFromZipResponse(
        Long projectVersionId,
        String sourceRootPath,
        String uploadZipPath
    ) {}

    /**
     * Git 기반 project_version 생성 + clone + source_cache 생성
     *
     * @param projectId project PK
     * @param req 요청 DTO
     * @return 생성 결과
     */
    @PostMapping("/{projectId}/versions/git")
    public ResponseEntity<CreateFromGitResponse> createFromGit(
        @PathVariable @NotNull Long projectId,
        @RequestBody @Valid CreateFromGitRequest req
    ) {
        Instant expiresAt = parseInstantOrNull(req.expiresAt());

        var res = gitUseCase.create(new CreateProjectVersionFromGitUseCase.Request(
            projectId,
            req.versionLabel(),
            req.gitUrl(),
            req.commitHash(),
            expiresAt
        ));

        return ResponseEntity.ok(new CreateFromGitResponse(res.projectVersionId(), res.sourceRootPath()));
    }

    /**
     * ZIP 업로드 기반 project_version 생성 + unzip + source_cache 생성
     *
     * form-data:
     * - versionLabel: string
     * - file: multipart(zip)
     * - expiresAt: (optional) ISO-8601 instant string
     *
     * @param projectId project PK
     * @param versionLabel version label
     * @param file zip file
     * @param expiresAt expiresAt(optional)
     * @return 생성 결과
     */
    @PostMapping(
        path = "/{projectId}/versions/zip",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<CreateFromZipResponse> createFromZip(
        @PathVariable @NotNull Long projectId,
        @RequestParam("versionLabel") @NotBlank String versionLabel,
        @RequestPart("file") MultipartFile file,
        @RequestParam(value = "expiresAt", required = false) String expiresAt
    ) {
        if (file == null || file.isEmpty()) {
        throw new IllegalArgumentException("zip file is required");
        }

        Instant exp = parseInstantOrNull(expiresAt);

        try (InputStream in = file.getInputStream()) {
        var res = zipUseCase.create(new CreateProjectVersionFromZipUseCase.Request(
            projectId,
            versionLabel,
            file.getOriginalFilename(),
            in,
            exp
        ));

        return ResponseEntity.ok(new CreateFromZipResponse(
            res.projectVersionId(),
            res.sourceRootPath(),
            res.uploadZipPath()
        ));
        } catch (Exception e) {
        throw new IllegalStateException("zip upload failed: " + e.getMessage(), e);
        }
    }

    /**
     * ISO-8601 Instant 문자열을 파싱한다(빈 값이면 null).
     *
     * @param s expiresAt string
     * @return Instant or null
     */
    private Instant parseInstantOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        return Instant.parse(s.trim());
    }
}