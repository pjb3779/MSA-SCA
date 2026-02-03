package buaa.msasca.sca.core.application.service;

import buaa.msasca.sca.core.application.usecase.CreateProjectUseCase;
import buaa.msasca.sca.core.port.out.persistence.ProjectPort;

public class CreateProjectService implements CreateProjectUseCase {

  private final ProjectPort projectPort;

  public CreateProjectService(ProjectPort projectPort) {
    this.projectPort = projectPort;
  }

  /**
   * 프로젝트를 생성
   * @param request 생성 요청
   * @return 생성 결과
   */
  @Override
  public Response create(Request request) {
    // description/repoUrl은 아직 API에서 안 받으니 null 처리 (추후 확장 가능)
    var created = projectPort.create(request.name(), null, null);
    return new Response(created.id());
  }

  /**
   * 반환할 projectId를 정규화
   * @param id 생성된 project PK
   * @return project PK
   */
  private Long projectCreatedId(Long id) {
    if (id == null) throw new IllegalStateException("project id must not be null");
    return id;
  }
}