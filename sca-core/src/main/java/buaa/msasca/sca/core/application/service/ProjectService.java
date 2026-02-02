package buaa.msasca.sca.core.application.service;

import buaa.msasca.sca.core.domain.model.Project;
import buaa.msasca.sca.core.port.out.persistence.ProjectPort;

import java.util.Optional;

public class ProjectService {

  private final ProjectPort projectPort;

  public ProjectService(ProjectPort projectPort) {
    this.projectPort = projectPort;
  }

  public Optional<Project> get(Long projectId) {
    return projectPort.findById(projectId);
  }
}