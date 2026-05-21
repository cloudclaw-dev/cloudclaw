package run.cloudclaw.common.exception;

import lombok.Getter;

/**
 * Business exception for application-level errors.
 * Used to signal expected business rule violations that should return
 * a meaningful error response to the client.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = 400;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
