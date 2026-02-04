package buaa.msasca.sca.infra.storage.local;

import buaa.msasca.sca.core.port.out.tool.StoragePort;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class LocalStoragePortAdapter implements StoragePort {

  private final Path baseDir;

  public LocalStoragePortAdapter() {
    this.baseDir = Path.of("/msasca/storage");
    try {
      Files.createDirectories(baseDir);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to init local storage: " + baseDir, e);
    }
  }

  /**
   * 데이터를 /msasca/storage 하위에 저장한다.
   *
   * @param key 저장 키
   * @param input 데이터 스트림
   * @return 저장 결과
   */
  @Override
  public StoredObject put(String key, InputStream input) {
    try {
      Path target = baseDir.resolve(key);
      Files.createDirectories(target.getParent());
      Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
      return new StoredObject(key, "file://" + target.toAbsolutePath());
    } catch (Exception e) {
      throw new IllegalStateException("Storage put failed: key=" + key, e);
    }
  }
}