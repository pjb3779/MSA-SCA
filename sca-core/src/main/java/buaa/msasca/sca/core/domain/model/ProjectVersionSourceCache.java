package buaa.msasca.sca.core.domain.model;

import java.time.Instant;

public record ProjectVersionSourceCache(
    Long id,
    Long projectVersionId,
    String storagePath,
    boolean isValid,
    Instant expiresAt
) {}
