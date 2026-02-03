package buaa.msasca.sca.core.application.usecase;

import java.util.Optional;

import buaa.msasca.sca.core.domain.model.Project;

public interface GetProjectUseCase {
    Optional<Project> get(Long projectId);
}
