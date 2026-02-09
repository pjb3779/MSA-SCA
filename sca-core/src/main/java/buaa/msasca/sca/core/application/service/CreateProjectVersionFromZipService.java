package buaa.msasca.sca.core.application.service;

import buaa.msasca.sca.core.application.support.WorkspacePathResolver;
import buaa.msasca.sca.core.application.usecase.CreateProjectVersionFromZipUseCase;
import buaa.msasca.sca.core.domain.enums.SourceType;
import buaa.msasca.sca.core.port.out.persistence.ProjectPort;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionCommandPort;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionPort;
import buaa.msasca.sca.core.port.out.persistence.ProjectVersionSourceCacheCommandPort;
import buaa.msasca.sca.core.port.out.tool.RunnerPort;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class CreateProjectVersionFromZipService implements CreateProjectVersionFromZipUseCase {

  private final ProjectPort projectPort;
  private final ProjectVersionPort projectVersionPort;
  private final ProjectVersionCommandPort projectVersionCommandPort;
  private final ProjectVersionSourceCacheCommandPort cacheCommandPort;
  private final WorkspacePathResolver pathResolver;

  private final RunnerPort runnerPort;

  public CreateProjectVersionFromZipService(
      ProjectPort projectPort,
      ProjectVersionPort projectVersionPort,
      ProjectVersionCommandPort projectVersionCommandPort,
      ProjectVersionSourceCacheCommandPort cacheCommandPort,
      WorkspacePathResolver pathResolver,
      RunnerPort runnerPort
  ) {
    this.projectPort = projectPort;
    this.projectVersionPort = projectVersionPort;
    this.projectVersionCommandPort = projectVersionCommandPort;
    this.cacheCommandPort = cacheCommandPort;
    this.pathResolver = pathResolver;
    this.runnerPort = runnerPort;
  }

  /**
   * ZIP 업로드 기반으로 project_version 생성 + zip 저장 + unzip + source_cache 생성까지 처리한다.
   *
   * @param req 요청
   * @return 결과
   */
  @Override
  public Response create(Request req) {
    projectPort.findById(req.projectId())
        .orElseThrow(() -> new IllegalArgumentException("project not found: " + req.projectId()));

    if (req.zipInputStream() == null) {
      throw new IllegalArgumentException("zipInputStream is required");
    }

    // 1) project_version 생성 (uploadFilePath는 생성 직후 확정하므로 일단 null)
    var pv = projectVersionPort.create(
        req.projectId(),
        req.versionLabel(),
        SourceType.ZIP,
        null,
        null,
        null
    );

    // 2) 경로 확정 (Windows면 C:/msasca, Linux면 /msasca 로 설정값에 따라 결정됨)
    String sourceRoot = pathResolver.sourceRoot(req.projectId(), pv.id());
    String uploadZipPath = pathResolver.uploadZipPath(req.projectId(), pv.id(), req.originalFilename());

    // 3) 디렉토리 준비 + zip 저장
    ensureDir(Path.of(sourceRoot));
    ensureDir(Path.of(uploadZipPath).getParent());
    saveZip(req.zipInputStream(), Path.of(uploadZipPath));

    // 4) DB에 upload_file_path 반영
    projectVersionCommandPort.updateUploadFilePath(pv.id(), uploadZipPath);

    // 5) unzip -> sourceRoot
    unzipWindowsJava(Path.of(uploadZipPath), Path.of(sourceRoot));

    // 6) source cache 생성
    cacheCommandPort.createNewValid(pv.id(), sourceRoot, req.expiresAt());

    return new Response(pv.id(), sourceRoot, uploadZipPath);
  }

  /**
   * 디렉토리를 생성한다.
   *
   * @param dir 디렉토리
   */
  private void ensureDir(Path dir) {
    try {
      Files.createDirectories(dir);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to create dir: " + dir, e);
    }
  }

  /**
   * 업로드된 zip 스트림을 로컬 파일로 저장한다.
   *
   * @param in zip input stream
   * @param target 저장 대상 경로
   */
  private void saveZip(InputStream in, Path target) {
    try (InputStream input = in) {
      Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to save zip: " + target, e);
    }
  }

  /**
   * Windows용 unzip 구현(Java ZipInputStream).
   * ZIP Slip 방지 포함.
   *
   * @param zipPath zip 파일
   * @param destDir 목적지 디렉토리
   */
  private void unzipWindowsJava(Path zipPath, Path destDir) {
    try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (entry.getName() == null || entry.getName().isBlank()) continue;

        Path outPath = destDir.resolve(entry.getName()).normalize();

        // ZIP Slip 방지
        if (!outPath.startsWith(destDir)) {
          throw new IllegalStateException("Zip entry is outside target dir: " + entry.getName());
        }

        if (entry.isDirectory()) {
          Files.createDirectories(outPath);
          continue;
        }

        Files.createDirectories(outPath.getParent());
        Files.copy(zis, outPath, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (Exception e) {
      throw new IllegalStateException("Failed to unzip (windows/java): " + zipPath + " -> " + destDir, e);
    }
  }

  /**
   * Linux용 unzip 구현(unzip 커맨드).
   * unzip 패키지가 설치되어 있어야 한다.
   *
   * @param zipPath zip 파일
   * @param destDir 목적지 디렉토리
   */
  private void unzipLinuxCommand(Path zipPath, Path destDir) {
    ensureDir(destDir);

    RunnerPort.ExecResult res = runnerPort.run(new RunnerPort.ExecSpec(
        List.of("unzip", "-qq", zipPath.toString(), "-d", destDir.toString()),
        Map.of(),
        destDir.toString(),
        Duration.ofMinutes(5)
    ));

    if (res.exitCode() != 0) {
      throw new IllegalStateException(
          "Failed to unzip (linux/unzip) exitCode=" + res.exitCode()
              + "\nSTDOUT:\n" + res.stdout()
              + "\nSTDERR:\n" + res.stderr()
      );
    }
  }
}