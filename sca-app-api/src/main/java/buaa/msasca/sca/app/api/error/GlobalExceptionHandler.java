package buaa.msasca.sca.app.api.error;

import buaa.msasca.sca.core.application.error.DomainException;
import buaa.msasca.sca.core.application.error.InvalidConfigException;
import buaa.msasca.sca.core.application.error.ToolExecutionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidConfigException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidConfig(InvalidConfigException e) {
        return build(HttpStatus.BAD_REQUEST, e.errorCode(), e.getMessage());
    }

    @ExceptionHandler(ToolExecutionException.class)
    public ResponseEntity<ApiErrorResponse> handleToolExecution(ToolExecutionException e) {
        // 도구 실행 실패는 502 Bad Gateway 로 응답
        String msg = "분석 도구(" + e.toolName() + ") 실행 중 오류가 발생했습니다.";
        return build(HttpStatus.BAD_GATEWAY, e.errorCode(), msg);
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiErrorResponse> handleDomain(DomainException e) {
        return build(HttpStatus.BAD_REQUEST, e.errorCode(), e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(IllegalArgumentException e) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleServerError(Exception e) {
        String msg = (e.getMessage() == null) ? e.toString() : e.getMessage();
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", msg);
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(code, message));
    }
}