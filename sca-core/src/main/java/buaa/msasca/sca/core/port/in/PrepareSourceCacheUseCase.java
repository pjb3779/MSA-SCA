package buaa.msasca.sca.core.port.in;

import java.time.Instant;

import buaa.msasca.sca.core.domain.model.ProjectVersionSourceCache;

public interface PrepareSourceCacheUseCase {
    ProjectVersionSourceCache handle(Command command);

    record Command(
        Long projectVersionId,
        String storagePath,
        Instant expiresAt
    ) {}
}
