package buaa.msasca.sca.app.api.dto;

import java.time.Instant;

public record AutoPrepareSourceCacheRequest(
    Instant expiresAt,
    Boolean forceRefresh
) {}