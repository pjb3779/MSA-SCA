package buaa.msasca.sca.app.api.dto;

import java.time.Instant;

import buaa.msasca.sca.core.domain.enums.RunStatus;

public record AnalysisRunResponse(
    Long id,
    Long projectVersionId,
    RunStatus status,
    Instant startedAt,
    Instant finishedAt,
    Instant createdAt,
    Instant updatedAt,
    /** 등록된 프로젝트 PK (조회 시 project_version 통해 채움) */
    Long projectId,
    /** 사용자가 등록한 프로젝트 이름 */
    String projectName,
    /** 소스 캐시 루트 경로(있으면 그래프 루트 표시용) */
    String sourceStoragePath
) {}
