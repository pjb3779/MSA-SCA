package buaa.msasca.sca.core.domain.model;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

import buaa.msasca.sca.core.domain.enums.ArtifactType;

public record AnalysisArtifact(
    Long id,
    Long toolRunId,
    ArtifactType artifactType,
    String storagePath,
    JsonNode metadataJson,
    Instant createdAt
) {}
