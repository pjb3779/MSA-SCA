package buaa.msasca.sca.core.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import buaa.msasca.sca.core.domain.enums.GatewayYamlProvidedBy;
import buaa.msasca.sca.core.domain.model.AnalysisRun;
import buaa.msasca.sca.core.domain.model.ProjectVersionSourceCache;
import buaa.msasca.sca.core.domain.enums.RunStatus;
import buaa.msasca.sca.core.port.in.CreateAnalysisRunUseCase;
import buaa.msasca.sca.core.port.in.RequestMscanOnlyRunUseCase;
import buaa.msasca.sca.core.port.out.persistence.MscanGatewayYamlCommandPort;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionSourceCachePort;
import buaa.msasca.sca.core.port.out.tool.StoragePort;

import buaa.msasca.sca.core.application.service.RequestMscanOnlyRunService;

@ExtendWith(MockitoExtension.class)
class RequestMscanOnlyRunServiceTest {

  @Mock
  private ProjectVersionSourceCachePort sourceCachePort;

  @Mock
  private StoragePort storagePort;

  @Mock
  private MscanGatewayYamlCommandPort gatewayYamlCommandPort;

  @Mock
  private CreateAnalysisRunUseCase createAnalysisRunUseCase;

  @org.mockito.InjectMocks
  private RequestMscanOnlyRunService service;

  /**
   * 시나리오:
   * - source cache가 준비되어 있고
   * - gateway.yml과 jars.zip(.jar 포함)이 업로드된 상태에서
   * - MSCAN_ONLY 분석 런을 enqueues해야 함
   * 기대:
   * - gateway.yml이 소스캐시(.msasca/mscan/gateway.yml)로 스테이징
   * - jars.zip에서 .jar만 해제/스테이징하고 기존 jar 캐시는 비움
   * - createAnalysisRunUseCase로 전달되는 configJson에 pipeline.mode=MSCAN_ONLY 및 mscan 설정이 들어감
   * - gatewayYamlCommandPort.upsertReady가 메타데이터(sha256/size/testEndpoint)와 함께 호출됨
   */
  @Test
  void request_stagesGatewayAndJars_andEnqueuesMscanOnlyRun(@TempDir Path tempDir) throws Exception {
    Long projectVersionId = 10L;
    Long sourceCacheId = 999L;

    // 준비: 유효한 소스 캐시
    ProjectVersionSourceCache cache = new ProjectVersionSourceCache(
        sourceCacheId,
        projectVersionId,
        tempDir.toAbsolutePath().toString(),
        true,
        null
    );
    when(sourceCachePort.findValidByProjectVersionId(projectVersionId)).thenReturn(Optional.of(cache));

    // 준비: gateway.yml 내용 + 사용자 지정 파일명
    String gatewayText = "test: true\n";
    byte[] gwBytes = gatewayText.getBytes(StandardCharsets.UTF_8);

    String gwName = "gateway-user.yml";
    String expectedGwKey = "projectVersions/" + projectVersionId + "/mscan/gateway/" + gwName;
    String storedGwUri = "file:///stored/" + gwName;

    StoragePort.StoredObject storedGw = new StoragePort.StoredObject(expectedGwKey, storedGwUri);
    when(storagePort.put(eq(expectedGwKey), any(InputStream.class))).thenReturn(storedGw);

    // 준비: jars.zip 바이트(내부에 .jar만 있고, 무시될 파일 1개 포함)
    byte[] jarsZip = createZipBytes(Map.of(
        "a.jar", "jar-bytes",
        "README.txt", "ignored"
    ));

    // 추가로: 기존 jar 캐시 파일은 삭제되어야 한다.
    Path jarDir = tempDir.resolve(".msasca/mscan/jars");
    Files.createDirectories(jarDir);
    Path oldJar = jarDir.resolve("old.jar");
    Files.write(oldJar, "old".getBytes(StandardCharsets.UTF_8));
    assertNotNull(Files.exists(oldJar) ? oldJar : null);

    // 준비: CreateAnalysisRunUseCase가 반환할 분석 런 결과
    AnalysisRun created = new AnalysisRun(
        123L,
        projectVersionId,
        null,
        RunStatus.PENDING,
        null,
        null,
        "test",
        null,
        null
    );
    when(createAnalysisRunUseCase.handle(any(CreateAnalysisRunUseCase.Command.class))).thenReturn(created);

    // 실행: 요청 수행
    RequestMscanOnlyRunUseCase.Request req = new RequestMscanOnlyRunUseCase.Request(
        projectVersionId,
        null, // mscanName (빈 값 => pv-<id> 기본값)
        "classpath-keywords",
        null, // jvmArgs => 기본값 사용
        null, // reuse(재사용) => false
        null, // optionsFileRelPath(옵션 파일 상대 경로) 생략
        new ByteArrayInputStream(gwBytes),
        gwName,
        new ByteArrayInputStream(jarsZip),
        "jars.zip"
    );

    var res = service.request(req);

    // 검증: 응답
    assertEquals(projectVersionId, res.projectVersionId());
    assertEquals(created.id(), res.analysisRunId());
    assertEquals(1, res.jarCount());
    assertNotNull(res.gatewayCachePath());
    assertNotNull(res.jarCacheDir());
    assertEquals("enqueued MSCAN_ONLY run", res.message());

    // 검증: 캐시 파일이 고정된 상대 경로에 생성되어야 한다.
    Path expectedGatewayCache = tempDir.resolve(".msasca/mscan/gateway.yml");
    assertEquals(true, Files.exists(expectedGatewayCache));

    Path expectedJarCacheFile = jarDir.resolve("a.jar");
    assertEquals(true, Files.exists(expectedJarCacheFile));
    assertFalse(Files.exists(oldJar), "old.jar should be cleared before staging");

    // 검증: gatewayYamlCommandPort.upsertReady가 메타데이터와 함께 호출되어야 한다.
    ArgumentCaptor<ObjectNode> metaCap = ArgumentCaptor.forClass(ObjectNode.class);
    ArgumentCaptor<CreateAnalysisRunUseCase.Command> cmdCap =
        ArgumentCaptor.forClass(CreateAnalysisRunUseCase.Command.class);

    verify(gatewayYamlCommandPort).upsertReady(
        eq(projectVersionId),
        eq(GatewayYamlProvidedBy.USER_UPLOAD),
        eq(storedGwUri),
        any(String.class),
        eq(gwName),
        eq(".msasca/mscan/gateway.yml"),
        metaCap.capture()
    );

    // 메타데이터의 sha256이 gateway 바이트와 일치해야 한다.
    String expectedSha = sha256Hex(gwBytes);
    assertEquals(expectedSha, metaCap.getValue().get("sha256").asText());
    assertEquals(gwName, metaCap.getValue().get("filename").asText());
    assertEquals(gwBytes.length, metaCap.getValue().get("size").asInt());
    assertEquals(true, metaCap.getValue().get("testEndpoint").asBoolean());

    verify(createAnalysisRunUseCase).handle(cmdCap.capture());
    var cmd = cmdCap.getValue();
    assertEquals(projectVersionId, cmd.projectVersionId());
    assertEquals("test-mscan-only", cmd.triggeredBy());
    assertEquals(true, cmd.requireSourceCache());

    JsonNode cfg = cmd.configJson();
    assertEquals("MSCAN_ONLY", cfg.path("pipeline").path("mode").asText());
    assertEquals("pv-" + projectVersionId, cfg.path("mscan").path("name").asText());
    assertEquals("classpath-keywords", cfg.path("mscan").path("classpathKeywords").asText());
    String jvmArgs = cfg.path("mscan").path("jvmArgs").asText("");
    // 기본 JVM args는 로컬 환경/이전 반복에 따라 달라질 수 있다.
    assertTrue(
        jvmArgs.contains("-Xmx6g") || jvmArgs.contains("-Xmx2g"),
        "jvmArgs=" + jvmArgs
    );
    assertTrue(
        jvmArgs.contains("MaxMetaspaceSize=1g") || jvmArgs.contains("MaxMetaspaceSize=512m"),
        "jvmArgs=" + jvmArgs
    );
    assertEquals(false, cfg.path("mscan").path("reuse").asBoolean());
    assertEquals(true, cfg.path("mscan").hasNonNull("classpathKeywords"));
    assertNull(cfg.path("mscan").path("optionsFileRelPath").asText(null));
  }

  /**
   * 시나리오:
   * - gatewayFilename이 공백/빈 값으로 들어오는 경우
   * 기대:
   * - 기본 파일명인 'gateway.yml'로 upsertReady가 호출됨
   */
  @Test
  void request_gatewayFilename_blank_defaultsToGatewayYml(@TempDir Path tempDir) throws Exception {
    Long projectVersionId = 11L;

    when(sourceCachePort.findValidByProjectVersionId(projectVersionId)).thenReturn(Optional.of(
        new ProjectVersionSourceCache(1L, projectVersionId, tempDir.toString(), true, null)
    ));

    byte[] gwBytes = "k: v\n".getBytes(StandardCharsets.UTF_8);
    byte[] jarsZip = createZipBytes(Map.of("x.jar", "x"));

    String expectedGwKey = "projectVersions/" + projectVersionId + "/mscan/gateway/gateway.yml";
    StoragePort.StoredObject storedGw = new StoragePort.StoredObject(expectedGwKey, "file:///gateway.yml");
    when(storagePort.put(eq(expectedGwKey), any(InputStream.class))).thenReturn(storedGw);
    when(createAnalysisRunUseCase.handle(any(CreateAnalysisRunUseCase.Command.class))).thenReturn(
        new AnalysisRun(1L, projectVersionId, null, RunStatus.PENDING, null, null, "t", null, null)
    );

    var req = new RequestMscanOnlyRunUseCase.Request(
        projectVersionId,
        null,
        "kw",
        null,
        null,
        null,
        new ByteArrayInputStream(gwBytes),
        "   ", // 공백 => 기본 gateway.yml 파일명 사용
        new ByteArrayInputStream(jarsZip),
        "jars.zip"
    );

    service.request(req);

    verify(gatewayYamlCommandPort).upsertReady(
        eq(projectVersionId),
        eq(GatewayYamlProvidedBy.USER_UPLOAD),
        eq(storedGw.uri()),
        eq(sha256Hex(gwBytes)),
        eq("gateway.yml"),
        eq(".msasca/mscan/gateway.yml"),
        any(ObjectNode.class)
    );
  }

  /**
   * 시나리오:
   * - jars.zip에 .jar가 전혀 없는 경우
   * 기대:
   * - 'No .jar files found in jarsZip.' 오류로 예외가 발생함
   */
  @Test
  void request_whenZipHasNoJar_throws() throws Exception {
    Long projectVersionId = 12L;

    // jars.zip에 *.jar가 없으면 storage/upsert 이전에 실패해야 한다.
    when(sourceCachePort.findValidByProjectVersionId(projectVersionId)).thenReturn(Optional.of(
        new ProjectVersionSourceCache(1L, projectVersionId, ".", true, null)
    ));

    byte[] gwBytes = "k: v\n".getBytes(StandardCharsets.UTF_8);
    byte[] jarsZip = createZipBytes(Map.of("README.txt", "ignored"));

    when(storagePort.put(any(), any(InputStream.class))).thenReturn(
        new StoragePort.StoredObject("k", "file:///k")
    );

    var ex = assertThrows(IllegalStateException.class, () -> service.request(
        new RequestMscanOnlyRunUseCase.Request(
            projectVersionId,
            null,
            "kw",
            null,
            null,
            null,
            new ByteArrayInputStream(gwBytes),
            "gateway.yml",
            new ByteArrayInputStream(jarsZip),
            "jars.zip"
        )
    ));
    assertTrue(ex.getMessage().contains("No .jar files found"));
  }

  private static byte[] createZipBytes(Map<String, String> entries) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(bos)) {
      for (var e : entries.entrySet()) {
        ZipEntry ze = new ZipEntry(e.getKey());
        zos.putNextEntry(ze);
        zos.write(e.getValue().getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
      }
    }
    return bos.toByteArray();
  }

  private static String sha256Hex(byte[] bytes) throws Exception {
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    md.update(bytes);
    String hex = java.util.HexFormat.of().formatHex(md.digest());
    return hex;
  }
}

