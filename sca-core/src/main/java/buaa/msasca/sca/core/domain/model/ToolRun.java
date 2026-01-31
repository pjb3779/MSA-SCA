package buaa.msasca.sca.core.domain.model;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

import buaa.msasca.sca.core.domain.enums.RunStatus;
import buaa.msasca.sca.core.domain.enums.ToolType;

public record ToolRun(
    Long id,
    Long analysisRunId,
    ToolType toolType,
    String toolVersion,
    JsonNode configJson,
    RunStatus status,
    Instant startedAt,
    Instant finishedAt,
    String errorMessage,
    Instant createdAt,
    Instant updatedAt
) {}
