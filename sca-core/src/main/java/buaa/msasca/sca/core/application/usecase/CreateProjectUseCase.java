package buaa.msasca.sca.core.application.usecase;

public interface CreateProjectUseCase {
  Response create(Request req);

  record Request(String name) {}

  record Response(Long projectId) {}
}