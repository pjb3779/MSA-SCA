package buaa.msasca.sca.infra.storage.local;

import buaa.msasca.sca.core.port.out.tool.StoragePort;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class LocalStoragePortAdapter implements StoragePort {

  private final Path baseDir;

  public LocalStoragePortAdapter() {
    this.baseDir = initBaseDir();
  }

  /**
   * 기본 경로(/msasca/storage)를 우선 사용하고,
   * 생성/권한 문제 등으로 실패하면 user.home 하위로 fallback 한다.
   *
   * @return 사용 가능한 baseDir
   */
  private Path initBaseDir() {
    Path primary = Path.of("/msasca/storage");
    try {
      Files.createDirectories(primary);
      return primary;
    } catch (Exception ignored) {
      Path fallback = Path.of(System.getProperty("user.home"), "msasca-storage");
      try {
        Files.createDirectories(fallback);
        return fallback;
      } catch (Exception e) {
        throw new IllegalStateException("Failed to init local storage: " + primary + " / " + fallback, e);
      }
    }
  }

  /**
   * 데이터를 baseDir 하위에 저장한다.
   *
   * @param key 저장 키
   * @param input 데이터 스트림
   * @return 저장 결과
   */
  @Override
  public StoredObject put(String key, InputStream input) {
    try {
      String normalizedKey = normalizeKey(key);
      Path target = baseDir.resolve(normalizedKey);

      Files.createDirectories(target.getParent());
      Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);

      return new StoredObject(normalizedKey, target.toUri().toString());
    } catch (Exception e) {
      throw new IllegalStateException("Storage put failed: key=" + key, e);
    }
  }

  /**
   * key를 OS 경로로 안전하게 정규화한다.
   * - 윈도우 역슬래시를 슬래시로 변환
   * - 앞의 '/' 제거
   *
   * @param key 저장 키
   * @return 정규화된 상대 경로
   */
  private String normalizeKey(String key) {
    if (key == null) return "";
    String k = key.replace("\\", "/");
    while (k.startsWith("/")) k = k.substring(1);
    return k;
  }
}