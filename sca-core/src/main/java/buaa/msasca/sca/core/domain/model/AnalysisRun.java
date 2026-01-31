package buaa.msasca.sca.core.domain.model;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

import buaa.msasca.sca.core.domain.enums.RunStatus;

public record AnalysisRun(
    Long id,
    Long projectVersionId,
    JsonNode configJson,
    RunStatus status,
    Instant startedAt,
    Instant finishedAt,
    String triggeredBy,
    Instant createdAt,
    Instant updatedAt
) {
    public boolean isTerminal() {
        return status == RunStatus.DONE || status == RunStatus.FAILED;
    }
}
