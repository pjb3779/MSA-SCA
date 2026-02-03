package buaa.msasca.sca.app.api.error;

import java.time.Instant;

public record ApiErrorResponse(
    Instant timestamp,
    String error,
    String message
) {
  public static ApiErrorResponse of(String error, String message) {
    return new ApiErrorResponse(Instant.now(), error, message);
  }
}