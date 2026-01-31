package buaa.msasca.sca.infra.storage;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import buaa.msasca.sca.core.port.out.tool.StoragePort;

public class NoopStorageAdapter implements StoragePort {
  private final Map<String, byte[]> inMemory = new ConcurrentHashMap<>();

  @Override
  public StoredObject put(String key, InputStream data) {
    try {
      inMemory.put(key, data.readAllBytes());
      return new StoredObject(key, "memory://" + key);
    } catch (Exception e) {
      throw new RuntimeException("Failed to store object: " + key, e);
    }
  }

  @Override
  public InputStream get(String key) {
    var bytes = inMemory.get(key);
    if (bytes == null) throw new IllegalArgumentException("Object not found: " + key);
    return new ByteArrayInputStream(bytes);
  }

  @Override
  public boolean exists(String key) {
    return inMemory.containsKey(key);
  }
}
