package buaa.msasca.sca.core.application.usecase;

public interface RequestRunUseCase {
  Response request(Request request);

  record Request(String projectId) {}
  record Response(String runId) {}
}
