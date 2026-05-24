package run.cloudclaw.common.exception;

import run.cloudclaw.common.dto.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.ConnectException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(BusinessException e) {
        log.warn("Business exception: code={}, i18nKey={}, message={}", e.getCode(), e.getI18nKey(), e.getMessage());

        HttpStatus status = mapToHttpStatus(e.getCode());

        String message = e.getFallbackMsg() != null ? e.getFallbackMsg() : e.getMessage();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", e.getCode());
        body.put("message", message);
        body.put("data", null);
        if (e.getI18nKey() != null) {
            body.put("i18nKey", e.getI18nKey());
        }
        if (e.getArgs() != null && e.getArgs().length > 0) {
            body.put("args", e.getArgs());
        }

        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", fieldErrors);
        return Result.error(400, fieldErrors);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoResourceFound(NoResourceFoundException e) {
        log.debug("Resource not found: {}", e.getResourcePath());
        return Result.error(404, "Resource not found");
    }

    @ExceptionHandler({org.springframework.web.reactive.function.client.WebClientResponseException.class,
            ConnectException.class, TimeoutException.class})
    public ResponseEntity<Result<Void>> handleExternalServiceException(Exception e) {
        log.error("External service error: {}", e.getMessage(), e);
        // Fix M4: This 5002 code is for external service unavailability (not session access denied).
        // SESSION_ACCESS_DENIED was changed to 5003 to avoid collision with this value.
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Result.error(5002, "common.serviceUnavailable"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleGenericException(Exception e, HttpServletRequest request) {
        // Fix M5: SSE errors now return a proper 500 response instead of null
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("text/event-stream")) {
            log.error("SSE stream error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new Result<>(500, "Stream error", null));
        }
        log.error("Unexpected error on {}: {}", request.getRequestURI(), e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(5000, "common.internalError"));
    }

    private HttpStatus mapToHttpStatus(int code) {
        return switch (code) {
            case 401, 1001 -> HttpStatus.UNAUTHORIZED;
            case 403, 1003 -> HttpStatus.FORBIDDEN;
            case 404, 1004 -> HttpStatus.NOT_FOUND;
            case 409, 1009 -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
