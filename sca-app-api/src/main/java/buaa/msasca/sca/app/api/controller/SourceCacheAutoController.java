package buaa.msasca.sca.app.api.controller;

import org.springframework.web.bind.annotation.RestController;

import buaa.msasca.sca.app.api.dto.AutoPrepareSourceCacheRequest;
import buaa.msasca.sca.app.api.dto.SourceCacheResponse;
import buaa.msasca.sca.core.port.in.PrepareSourceCacheAutoUseCase;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
@RequestMapping("/api")
public class SourceCacheAutoController {

    private final PrepareSourceCacheAutoUseCase useCase;

    public SourceCacheAutoController(PrepareSourceCacheAutoUseCase useCase) {
        this.useCase = useCase;
    }

    /**
     * project_version의 source_type에 따라 소스를 자동 준비하고
     * project_version_source_cache를 갱신한다.
     */
    @PostMapping("/project-versions/{projectVersionId}/source-cache/auto")
    public SourceCacheResponse autoPrepare(
        @PathVariable Long projectVersionId,
        @RequestBody AutoPrepareSourceCacheRequest req
    ) {
        boolean force = (req.forceRefresh() != null) && req.forceRefresh();
        var cache = useCase.handle(new PrepareSourceCacheAutoUseCase.Command(
            projectVersionId,
            req.expiresAt(),
            force
        ));

        return new SourceCacheResponse(
            cache.id(),
            cache.projectVersionId(),
            cache.storagePath(),
            cache.isValid(),
            cache.expiresAt()
        );
    }
}