package buaa.msasca.sca.app.api.advice;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @Data
  @AllArgsConstructor
  static class ErrorResponse {
    private String message;
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> illegalArg(IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException e) {
    return ResponseEntity.badRequest().body(new ErrorResponse("validation error"));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> other(Exception e) {
    return ResponseEntity.internalServerError().body(new ErrorResponse(e.getMessage()));
  }
}
