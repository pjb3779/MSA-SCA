package buaa.msasca.sca.app.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import buaa.msasca.sca.core.application.usecase.CreateProjectUseCase;
import buaa.msasca.sca.core.application.usecase.GetProjectUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final CreateProjectUseCase createProjectUseCase;
    private final GetProjectUseCase getProjectUseCase;

    public ProjectController(CreateProjectUseCase createProjectUseCase, GetProjectUseCase getProjectUseCase) {
        this.createProjectUseCase = createProjectUseCase;
        this.getProjectUseCase = getProjectUseCase;
    }

    public record CreateProjectRequest(
        @NotBlank String name
    ) {}

    public record CreateProjectResponse(
        Long projectId
    ) {}

    public record ProjectResponse(
        Long id,
        String name,
        String description,
        String repoUrl
    ) {}

    /**
     * 프로젝트를 생성한다.
     * @param req 프로젝트 생성 요청 DTO
     * @return 생성된 projectId
     */
    @PostMapping
    public ResponseEntity<CreateProjectResponse> create(@RequestBody @Valid CreateProjectRequest req) {
        var res = createProjectUseCase.create(new CreateProjectUseCase.Request(req.name()));
        return ResponseEntity.ok(new CreateProjectResponse(res.projectId()));
    }

    /**
     * 프로젝트를 조회한다.
     * @param projectId project PK
     * @return 프로젝트 정보(없으면 404)
     */
    @GetMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> get(@PathVariable Long projectId) {
        return getProjectUseCase.get(projectId)
            .map(p -> ResponseEntity.ok(new ProjectResponse(p.id(), p.name(), p.description(), p.repoUrl())))
            .orElseGet(() -> ResponseEntity.notFound().build());
    } 
}