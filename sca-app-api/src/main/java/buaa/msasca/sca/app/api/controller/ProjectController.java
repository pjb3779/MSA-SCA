package buaa.msasca.sca.app.api.controller;

import buaa.msasca.sca.core.application.usecase.CreateProjectUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {
  private final CreateProjectUseCase createProjectUseCase;

  public ProjectController(CreateProjectUseCase createProjectUseCase) {
    this.createProjectUseCase = createProjectUseCase;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CreateProjectRequest {
    @NotBlank
    private String name;
  }

  @Data
  @AllArgsConstructor
  public static class CreateProjectResponse {
    private String projectId;
  }

  @PostMapping
  public ResponseEntity<CreateProjectResponse> create(@RequestBody @Valid CreateProjectRequest req) {
    var res = createProjectUseCase.create(new CreateProjectUseCase.Request(req.getName()));
    return ResponseEntity.ok(new CreateProjectResponse(res.projectId()));
  }
}
