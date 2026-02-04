package buaa.msasca.sca.app.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import buaa.msasca.sca.app.api.dto.PrepareSourceCacheRequest;
import buaa.msasca.sca.app.api.dto.SourceCacheResponse;
import buaa.msasca.sca.core.port.in.GetSourceCacheUseCase;
import buaa.msasca.sca.core.port.in.PrepareSourceCacheUseCase;

// @RestController
// @RequestMapping("/api")
public class SourceCacheController {

    // private final PrepareSourceCacheUseCase prepareUseCase;
    // private final GetSourceCacheUseCase getUseCase;

    // public SourceCacheController(
    //     PrepareSourceCacheUseCase prepareUseCase,
    //     GetSourceCacheUseCase getUseCase
    // ) {
    //     this.prepareUseCase = prepareUseCase;
    //     this.getUseCase = getUseCase;
    // }

    // /**
    //  * project_version에 대한 source cache 생성
    //  *
    //  * 동작:
    //  * - 기존 cache는 모두 invalidate 처리
    //  * - 새로운 cache를 valid 상태로 생성
    //  *
    //  * @param projectVersionId 프로젝트 버전 ID
    //  * @param req storagePath, expiresAt 정보
    //  * @return 생성된 source cache 정보
    //  */
    // @PostMapping("/project-versions/{projectVersionId}/source-cache")
    // public SourceCacheResponse prepare(
    //     @PathVariable Long projectVersionId,
    //     @RequestBody PrepareSourceCacheRequest req
    // ) {
    //     var cache = prepareUseCase.handle(
    //         new PrepareSourceCacheUseCase.Command(
    //             projectVersionId,
    //             req.storagePath(),
    //             req.expiresAt()
    //         )
    //     );

    //     return new SourceCacheResponse(
    //         cache.id(),
    //         cache.projectVersionId(),
    //         cache.storagePath(),
    //         cache.isValid(),
    //         cache.expiresAt()
    //     );
    // }

    // /**
    //  * project_version의 현재 유효한 source cache 조회
    //  *
    //  * @param projectVersionId 프로젝트 버전 ID
    //  * @return 유효한 source cache
    //  */
    // @GetMapping("/project-versions/{projectVersionId}/source-cache")
    // public SourceCacheResponse get(@PathVariable Long projectVersionId) {
    //     var cache = getUseCase.findValidByProjectVersionId(projectVersionId)
    //         .orElseThrow(() ->
    //             new IllegalStateException(
    //                 "No valid source cache for projectVersionId=" + projectVersionId
    //             )
    //         );

    //     return new SourceCacheResponse(
    //         cache.id(),
    //         cache.projectVersionId(),
    //         cache.storagePath(),
    //         cache.isValid(),
    //         cache.expiresAt()
    //     );
    // }
}
