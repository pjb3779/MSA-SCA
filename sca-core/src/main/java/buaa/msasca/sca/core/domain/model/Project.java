package buaa.msasca.sca.core.domain.model;

import java.time.Instant;

public record Project(
    Long id,
    String name,
    String description,
    String repoUrl,
    Instant createdAt,
    Instant updatedAt
) {}
