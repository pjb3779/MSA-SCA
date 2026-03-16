package buaa.msasca.sca.app.api.controller;

import java.time.Instant;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import buaa.msasca.sca.app.api.dto.MscanGatewayYamlResponse;
import buaa.msasca.sca.core.application.service.EnqueueAnalysisRunOnSourceReadyService;
import buaa.msasca.sca.core.application.support.MscanConfigAutoBuilder;
import buaa.msasca.sca.core.domain.enums.GatewayYamlProvidedBy;
import buaa.msasca.sca.core.port.in.EnsureMscanGatewayYamlUseCase;
import buaa.msasca.sca.core.port.in.GetMscanGatewayYamlUseCase;
import buaa.msasca.sca.core.port.in.UploadMscanGatewayYamlUseCase;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionSourceCachePort;

@RestController
@RequestMapping("/api/project-versions/{projectVersionId}/mscan/gateway-yaml")
public class MscanGatewayYamlController {

    private final UploadMscanGatewayYamlUseCase uploadUseCase;
    private final GetMscanGatewayYamlUseCase getUseCase;
    private final EnsureMscanGatewayYamlUseCase ensureUseCase;

    private final ProjectVersionSourceCachePort sourceCachePort;
    private final EnqueueAnalysisRunOnSourceReadyService enqueueService;

    public MscanGatewayYamlController(
        UploadMscanGatewayYamlUseCase uploadUseCase,
        GetMscanGatewayYamlUseCase getUseCase,
        EnsureMscanGatewayYamlUseCase ensureUseCase,
        ProjectVersionSourceCachePort sourceCachePort,
        EnqueueAnalysisRunOnSourceReadyService enqueueService
    ) {
        this.uploadUseCase = uploadUseCase;
        this.getUseCase = getUseCase;
        this.ensureUseCase = ensureUseCase;
        this.sourceCachePort = sourceCachePort;
        this.enqueueService = enqueueService;
    }

    /**
     * gateway.yml 상태 조회
     */
    @GetMapping
    public MscanGatewayYamlResponse get(@PathVariable Long projectVersionId) {
        var y = getUseCase.get(projectVersionId)
            .orElseGet(() -> ensureUseCase.ensure(projectVersionId));

        return MscanGatewayYamlResponse.from(y);
    }

    /**
     * 사용자 gateway.yml 업로드
     * → READY 전환
     * → analysis_run 자동 재실행
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MscanGatewayYamlResponse upload(
        @PathVariable Long projectVersionId,
        @RequestPart("file") MultipartFile file
    ) throws Exception {

        var saved = uploadUseCase.upload(new UploadMscanGatewayYamlUseCase.Request(
            projectVersionId,
            file.getOriginalFilename(),
            file.getInputStream(),
            GatewayYamlProvidedBy.USER_UPLOAD
        ));

        // source cache 확인
        var cache = sourceCachePort.findValidByProjectVersionId(projectVersionId)
            .orElseThrow(() -> new IllegalStateException(
                "Source cache not ready for projectVersionId=" + projectVersionId
            ));

        // 기존 config와 동일한 기본 config 생성
        var config = MscanConfigAutoBuilder.buildDefaultConfig(
            projectVersionId,
            cache.storagePath()
        );

        // analysis_run 재실행
        enqueueService.enqueueIfAbsent(projectVersionId, config, "user");

        return MscanGatewayYamlResponse.from(saved);
    }
}