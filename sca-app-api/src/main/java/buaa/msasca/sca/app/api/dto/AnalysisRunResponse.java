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
    Instant updatedAt
) {}
