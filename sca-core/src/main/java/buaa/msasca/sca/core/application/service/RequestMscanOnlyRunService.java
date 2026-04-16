package buaa.msasca.sca.core.application.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import buaa.msasca.sca.core.domain.enums.GatewayYamlProvidedBy;
import buaa.msasca.sca.core.port.in.CreateAnalysisRunUseCase;
import buaa.msasca.sca.core.port.in.RequestMscanOnlyRunUseCase;
import buaa.msasca.sca.core.port.out.persistence.MscanGatewayYamlCommandPort;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionSourceCachePort;
import buaa.msasca.sca.core.port.out.tool.StoragePort;

public class RequestMscanOnlyRunService implements RequestMscanOnlyRunUseCase {

  private static final String MSCAN_GATEWAY_REL_PATH = ".msasca/mscan/gateway.yml";
  private static final String MSCAN_JAR_DIR_REL_PATH = ".msasca/mscan/jars";

  private final ProjectVersionSourceCachePort sourceCachePort;
  private final StoragePort storagePort;
  private final MscanGatewayYamlCommandPort gatewayYamlCommandPort;
  private final CreateAnalysisRunUseCase createAnalysisRunUseCase;

  private final ObjectMapper om = new ObjectMapper();

  public RequestMscanOnlyRunService(
      ProjectVersionSourceCachePort sourceCachePort,
      StoragePort storagePort,
      MscanGatewayYamlCommandPort gatewayYamlCommandPort,
      CreateAnalysisRunUseCase createAnalysisRunUseCase
  ) {
    this.sourceCachePort = sourceCachePort;
    this.storagePort = storagePort;
    this.gatewayYamlCommandPort = gatewayYamlCommandPort;
    this.createAnalysisRunUseCase = createAnalysisRunUseCase;
  }

  @Override
  public Response request(Request req) {
    var cache = sourceCachePort.findValidByProjectVersionId(req.projectVersionId())
        .orElseThrow(() -> new IllegalStateException("Source cache is not ready. pv=" + req.projectVersionId()));

    String sourceRoot = cache.storagePath();

    Path gatewayPath = Path.of(sourceRoot).resolve(MSCAN_GATEWAY_REL_PATH).normalize();
    Path jarDir = Path.of(sourceRoot).resolve(MSCAN_JAR_DIR_REL_PATH).normalize();

    try {
      // 1) gateway.yml staging + DB READY upsert (필수)
      if (req.gatewayYaml() == null) {
        throw new IllegalArgumentException("gatewayYaml is required");
      }
      byte[] gwBytes = readAll(req.gatewayYaml());
      Files.createDirectories(gatewayPath.getParent());
      Files.write(gatewayPath, gwBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

      String gwSha = sha256(gwBytes);
      String gwName = (req.gatewayFilename() == null || req.gatewayFilename().isBlank())
          ? "gateway.yml"
          : req.gatewayFilename();

      String gwKey = "projectVersions/" + req.projectVersionId() + "/mscan/gateway/" + gwName;
      var storedGw = storagePort.put(gwKey, new ByteArrayInputStream(gwBytes));

      ObjectNode gwMeta = om.createObjectNode();
      gwMeta.put("filename", gwName);
      gwMeta.put("sha256", gwSha);
      gwMeta.put("size", gwBytes.length);
      gwMeta.put("testEndpoint", true);

      gatewayYamlCommandPort.upsertReady(
          req.projectVersionId(),
          GatewayYamlProvidedBy.USER_UPLOAD,
          storedGw.uri(),
          gwSha,
          gwName,
          MSCAN_GATEWAY_REL_PATH,
          gwMeta
      );

      // 2) jar dir 준비 + 기존 파일 삭제
      Files.createDirectories(jarDir);
      clearDirFiles(jarDir);

      // 3) jars.zip 풀기 (ZIP Slip 방지 + .jar만 허용)
      int jarCount = unzipJarsZipToDir(req.jarsZip(), jarDir);

      if (jarCount == 0) {
        throw new IllegalArgumentException("No .jar files found in jarsZip.");
      }

      // 4) MSCAN_ONLY analysis_run 생성
      ObjectNode cfg = om.createObjectNode();

      ObjectNode pipeline = cfg.putObject("pipeline");
      pipeline.put("mode", "MSCAN_ONLY");

      ObjectNode mscan = cfg.putObject("mscan");
      mscan.put("name", isBlank(req.mscanName()) ? ("pv-" + req.projectVersionId()) : req.mscanName());
      mscan.put("classpathKeywords", req.classpathKeywords()); // 필수
      mscan.put("jvmArgs", isBlank(req.jvmArgs()) ? "-Xmx6g -XX:MaxMetaspaceSize=1g" : req.jvmArgs());
      mscan.put("reuse", req.reuse() != null && req.reuse());
      if (!isBlank(req.optionsFileRelPath())) {
        mscan.put("optionsFileRelPath", req.optionsFileRelPath());
      }

      var created = createAnalysisRunUseCase.handle(new CreateAnalysisRunUseCase.Command(
          req.projectVersionId(),
          cfg,
          "test-mscan-only",
          true
      ));

      if (created == null) {
        return new Response(
            req.projectVersionId(),
            null,
            jarCount,
            gatewayPath.toString(),
            jarDir.toString(),
            "active run already exists (skipped)"
        );
      }

      return new Response(
          req.projectVersionId(),
          created.id(),
          jarCount,
          gatewayPath.toString(),
          jarDir.toString(),
          "enqueued MSCAN_ONLY run"
      );

    } catch (Exception e) {
      throw new IllegalStateException("Failed to stage gateway/jarsZip and enqueue MSCAN_ONLY run: " + e.getMessage(), e);
    }
  }

  /** jarsZip InputStream을 jarDir로 해제한다. (.jar만 저장, ZIP Slip 방지) */
  private int unzipJarsZipToDir(InputStream jarsZip, Path jarDir) throws Exception {
    if (jarsZip == null) throw new IllegalArgumentException("jarsZip is required");

    int jarCount = 0;

    try (ZipInputStream zis = new ZipInputStream(jarsZip)) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (entry.getName() == null || entry.getName().isBlank()) continue;
        if (entry.isDirectory()) continue;

        String name = entry.getName().replace("\\", "/");
        String lower = name.toLowerCase();

        // ✅ .jar만 허용 (그 외 파일은 무시하거나, 엄격히 막고 싶으면 throw로 바꿔도 됨)
        if (!lower.endsWith(".jar")) continue;

        // ZIP Slip 방지: jarDir 하위로만
        Path out = jarDir.resolve(name).normalize();
        if (!out.startsWith(jarDir)) {
          throw new IllegalStateException("Zip entry is outside target dir: " + name);
        }

        Files.createDirectories(out.getParent());

        try (OutputStream os = Files.newOutputStream(out, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
          zis.transferTo(os);
        }

        jarCount++;
      }
    }

    return jarCount;
  }

  private static void clearDirFiles(Path dir) throws Exception {
    try (var s = Files.list(dir)) {
      s.filter(Files::isRegularFile).forEach(p -> {
        try { Files.deleteIfExists(p); } catch (Exception ignored) {}
      });
    }
  }

  private static byte[] readAll(InputStream in) throws Exception {
    try (InputStream input = in) {
      return input.readAllBytes();
    }
  }

  private static String sha256(byte[] bytes) throws Exception {
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    md.update(bytes);
    return HexFormat.of().formatHex(md.digest());
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}