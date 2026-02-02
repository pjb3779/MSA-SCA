package buaa.msasca.sca.app.api.dto;

import java.time.Instant;

public record SourceCacheResponse(
    Long id,
    Long projectVersionId,
    String storagePath,
    boolean isValid,
    Instant expiresAt
) {}
