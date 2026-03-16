package buaa.msasca.sca.app.api.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import buaa.msasca.sca.core.port.in.RequestMscanOnlyRunUseCase;

@RestController
@RequestMapping("/api/test")
public class TestMscanOnlyController {

  private final RequestMscanOnlyRunUseCase useCase;

  public TestMscanOnlyController(RequestMscanOnlyRunUseCase useCase) {
    this.useCase = useCase;
  }

  public record ResponseDto(
      Long projectVersionId,
      Long analysisRunId,
      int jarCount,
      String gatewayCachePath,
      String jarCacheDir,
      String message
  ) {}

  @PostMapping(
      path = "/mscan-only",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE
  )
  public ResponseEntity<ResponseDto> runMscanOnly(
      @RequestParam @NotNull Long projectVersionId,
      @RequestParam @NotBlank String classpathKeywords,
      @RequestParam(required = false) String mscanName,
      @RequestParam(required = false) String jvmArgs,
      @RequestParam(required = false) Boolean reuse,
      @RequestPart("gatewayYaml") MultipartFile gatewayYaml,
      @RequestPart("jarsZip") MultipartFile jarsZip
  ) throws Exception {

    if (gatewayYaml == null || gatewayYaml.isEmpty()) {
      throw new IllegalArgumentException("gatewayYaml is required");
    }
    if (jarsZip == null || jarsZip.isEmpty()) {
      throw new IllegalArgumentException("jarsZip is required (.zip containing *.jar)");
    }

    var res = useCase.request(new RequestMscanOnlyRunUseCase.Request(
        projectVersionId,
        mscanName,
        classpathKeywords,
        jvmArgs,
        reuse,
        null, // optionsFileRelPath
        gatewayYaml.getInputStream(),
        gatewayYaml.getOriginalFilename(),
        jarsZip.getInputStream(),
        jarsZip.getOriginalFilename()
    ));

    return ResponseEntity.ok(new ResponseDto(
        res.projectVersionId(),
        res.analysisRunId(),
        res.jarCount(),
        res.gatewayCachePath(),
        res.jarCacheDir(),
        res.message()
    ));
  }
}