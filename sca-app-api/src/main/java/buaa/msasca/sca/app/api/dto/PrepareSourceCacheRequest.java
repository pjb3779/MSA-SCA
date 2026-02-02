package buaa.msasca.sca.app.api.dto;

import java.time.Instant;

public record PrepareSourceCacheRequest(
    String storagePath,
    Instant expiresAt
) {}
