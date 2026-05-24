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
    private final String i18nKey;
    private final String fallbackMsg;
    private final Object[] args;

    public BusinessException(String message) {
        super(message);
        this.code = 400;
        this.i18nKey = null;
        this.fallbackMsg = null;
        this.args = null;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
        this.i18nKey = null;
        this.fallbackMsg = null;
        this.args = null;
    }

    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.i18nKey = null;
        this.fallbackMsg = null;
        this.args = null;
    }

    /**
     * Construct from ErrorCode with optional i18n message parameters.
     */
    public BusinessException(ErrorCode errorCode, Object... args) {
        super(errorCode.getI18nKey());
        this.code = errorCode.getCode();
        this.i18nKey = errorCode.getI18nKey();
        this.fallbackMsg = null;
        this.args = args;
    }

    /**
     * Construct from ErrorCode with a fallback message and optional parameters.
     */
    public BusinessException(ErrorCode errorCode, String fallbackMsg, Object... args) {
        super(fallbackMsg != null ? fallbackMsg : errorCode.getI18nKey());
        this.code = errorCode.getCode();
        this.i18nKey = errorCode.getI18nKey();
        this.fallbackMsg = fallbackMsg;
        this.args = args;
    }
}
