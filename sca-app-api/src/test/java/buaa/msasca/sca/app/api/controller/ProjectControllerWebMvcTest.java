package buaa.msasca.sca.app.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import buaa.msasca.sca.app.api.error.GlobalExceptionHandler;
import buaa.msasca.sca.core.application.usecase.CreateProjectUseCase;
import buaa.msasca.sca.core.application.usecase.GetProjectUseCase;
import buaa.msasca.sca.core.domain.model.Project;

@ExtendWith(MockitoExtension.class)
class ProjectControllerWebMvcTest {

  MockMvc mockMvc;

  @Mock CreateProjectUseCase createProjectUseCase;
  @Mock GetProjectUseCase getProjectUseCase;

  @BeforeEach
  void setUp() {
    ProjectController controller = new ProjectController(createProjectUseCase, getProjectUseCase);
    this.mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  @Test
  void create_returnsProjectId() throws Exception {
    // 시나리오: 유효한 요청 바디를 받으면 생성된 projectId를 응답한다.
    when(createProjectUseCase.create(any())).thenReturn(new CreateProjectUseCase.Response(101L));

    mockMvc.perform(post("/api/projects")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":"demo-project"}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.projectId").value(101));

    verify(createProjectUseCase).create(any());
  }

  @Test
  void get_whenExists_returnsProjectResponse() throws Exception {
    // 시나리오: 프로젝트가 존재하면 200 + 프로젝트 정보를 반환한다.
    Project p = new Project(1L, "p1", "desc", "https://repo", Instant.now(), Instant.now());
    when(getProjectUseCase.get(eq(1L))).thenReturn(Optional.of(p));

    mockMvc.perform(get("/api/projects/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.name").value("p1"))
        .andExpect(jsonPath("$.description").value("desc"))
        .andExpect(jsonPath("$.repoUrl").value("https://repo"));
  }

  @Test
  void get_whenMissing_returnsNotFound() throws Exception {
    // 시나리오: 프로젝트가 없으면 404를 반환한다.
    when(getProjectUseCase.get(eq(999L))).thenReturn(Optional.empty());

    mockMvc.perform(get("/api/projects/999"))
        .andExpect(status().isNotFound());
  }
}

