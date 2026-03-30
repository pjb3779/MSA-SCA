package buaa.msasca.sca.app.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import buaa.msasca.sca.app.api.error.GlobalExceptionHandler;
import buaa.msasca.sca.core.port.in.RequestMscanOnlyRunUseCase;

@ExtendWith(MockitoExtension.class)
class TestMscanOnlyControllerWebMvcTest {

  MockMvc mockMvc;

  @Mock RequestMscanOnlyRunUseCase useCase;

  @BeforeEach
  void setUp() {
    TestMscanOnlyController controller = new TestMscanOnlyController(useCase);
    this.mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  @Test
  void runMscanOnly_success_returnsResponseDto() throws Exception {
    // 시나리오: gatewayYaml/jarsZip이 정상 전달되면 useCase 결과를 200 응답으로 반환한다.
    when(useCase.request(any())).thenReturn(new RequestMscanOnlyRunUseCase.Response(
        10L, 20L, 2, ".msasca/mscan/gateway.yml", ".msasca/mscan/jars", "ok"
    ));

    MockMultipartFile gateway = new MockMultipartFile(
        "gatewayYaml", "gateway.yml", "text/yaml", "a: b".getBytes()
    );
    MockMultipartFile jars = new MockMultipartFile(
        "jarsZip", "jars.zip", "application/zip", "PK".getBytes()
    );

    mockMvc.perform(multipart("/api/test/mscan-only")
            .file(gateway)
            .file(jars)
            .param("projectVersionId", "10")
            .param("classpathKeywords", "kw")
            .param("mscanName", "mscan-test"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.projectVersionId").value(10))
        .andExpect(jsonPath("$.analysisRunId").value(20))
        .andExpect(jsonPath("$.jarCount").value(2))
        .andExpect(jsonPath("$.message").value("ok"));

    verify(useCase).request(any());
  }

  @Test
  void runMscanOnly_missingGatewayYaml_returnsBadRequest() throws Exception {
    // 시나리오: gatewayYaml 파트가 비어 있으면 컨트롤러가 IllegalArgumentException을 던지고 400으로 매핑된다.
    MockMultipartFile gateway = new MockMultipartFile(
        "gatewayYaml", "gateway.yml", "text/yaml", new byte[0]
    );
    MockMultipartFile jars = new MockMultipartFile(
        "jarsZip", "jars.zip", "application/zip", "PK".getBytes()
    );

    mockMvc.perform(multipart("/api/test/mscan-only")
            .file(gateway)
            .file(jars)
            .param("projectVersionId", "10")
            .param("classpathKeywords", "kw"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
  }
}

