package buaa.msasca.sca.core.application.usecase;

public interface CreateProjectUseCase {
  Response create(Request request);

  record Request(String name) {}
  record Response(String projectId) {}
}
