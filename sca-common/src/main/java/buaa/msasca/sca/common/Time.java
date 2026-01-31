package buaa.msasca.sca.common;

import java.time.Instant;

public final class Time {
  private Time() {}
  public static Instant now() {
    return Instant.now();
  }
}
