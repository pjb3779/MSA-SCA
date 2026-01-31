package buaa.msasca.sca.common;

import java.util.UUID;

public final class Ids {
  private Ids() {}
  public static String newId() {
    return UUID.randomUUID().toString();
  }
}
