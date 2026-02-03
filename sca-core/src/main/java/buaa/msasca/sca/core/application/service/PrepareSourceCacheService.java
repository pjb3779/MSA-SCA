package buaa.msasca.sca.core.application.service;

import buaa.msasca.sca.core.domain.model.ProjectVersionSourceCache;
import buaa.msasca.sca.core.port.in.PrepareSourceCacheUseCase;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionSourceCacheCommandPort;

public class PrepareSourceCacheService implements PrepareSourceCacheUseCase {
    private final ProjectVersionSourceCacheCommandPort commandPort;

    public PrepareSourceCacheService(ProjectVersionSourceCacheCommandPort commandPort) {
        this.commandPort = commandPort;
    }

    @Override
    //실제 DB변경은 포트에 위임
    public ProjectVersionSourceCache handle(Command command) {
        return commandPort.createNewValid(command.projectVersionId(), command.storagePath(), command.expiresAt());
    }
}
