package buaa.msasca.sca.core.application.service;

import java.util.Optional;

import buaa.msasca.sca.core.application.usecase.GetProjectUseCase;
import buaa.msasca.sca.core.domain.model.Project;
import buaa.msasca.sca.core.port.out.persistence.ProjectPort;

public class GetProjectService implements GetProjectUseCase {

  private final ProjectPort projectPort;

  public GetProjectService(ProjectPort projectPort) {
    this.projectPort = projectPort;
  }

  /**
   * 프로젝트를 조회
   * @param projectId project PK
   * @return 존재하면 Project
   */
  @Override
  public Optional<Project> get(Long projectId) {
    return projectPort.findById(projectId);
  }
}