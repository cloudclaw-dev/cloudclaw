package run.cloudclaw.common.exception;

import lombok.Getter;

/**
 * Unified error codes for the application.
 * Note: These are BUSINESS error codes (not HTTP status codes).
 * They are returned in the response body as numeric identifiers for frontend i18n lookup.
 * Each error code has a numeric code and an i18n key that maps to frontend translations.
 */
@Getter
public enum ErrorCode {

    // Common 1xxx
    BAD_REQUEST(1000, "common.badRequest"),
    UNAUTHORIZED(1001, "common.unauthorized"),
    FORBIDDEN(1003, "common.forbidden"),
    NOT_FOUND(1004, "common.notFound"),
    CONFLICT(1009, "common.conflict"),

    // Agent 2xxx
    AGENT_NOT_FOUND(2001, "agent.notFound"),
    AGENT_SESSION_MODE_UNSUPPORTED(2002, "agent.sessionModeUnsupported"),

    // LLM 3xxx
    LLM_MODEL_NOT_FOUND(3001, "llm.modelNotFound"),
    LLM_PROVIDER_NOT_FOUND(3002, "llm.providerNotFound"),
    LLM_CREDENTIAL_NOT_FOUND(3003, "llm.credentialNotFound"),
    LLM_NO_AVAILABLE_CREDENTIAL(3004, "llm.noAvailableCredential"),
    LLM_CALL_FAILED(3005, "llm.callFailed"),
    LLM_CALL_TIMEOUT(3006, "llm.callTimeout"),
    LLM_UNSUPPORTED_PROVIDER(3007, "llm.unsupportedProvider"),
    LLM_API_KEY_INVALID(3008, "llm.apiKeyInvalid"),

    // MCP 4xxx
    MCP_SERVER_NOT_FOUND(4001, "mcp.serverNotFound"),
    MCP_CONNECTION_FAILED(4002, "mcp.connectionFailed"),
    MCP_CALL_TIMEOUT(4003, "mcp.callTimeout"),

    // Session 5xxx
    SESSION_NOT_FOUND(5001, "session.notFound"),
    // Fix M4: Changed from 5002 to 5003 to avoid conflict with hardcoded 5002 in GlobalExceptionHandler.handleExternalServiceException
    SESSION_ACCESS_DENIED(5003, "session.accessDenied"),

    // Auth 6xxx
    AUTH_INVALID_CREDENTIALS(6001, "auth.invalidCredentials"),
    AUTH_ACCOUNT_DISABLED(6002, "auth.accountDisabled"),
    AUTH_TOKEN_EXPIRED(6003, "auth.tokenExpired"),
    AUTH_INVALID_TOKEN(6004, "auth.invalidToken"),
    AUTH_RATE_LIMITED(6005, "auth.rateLimited"),

    // Skill 7xxx
    SKILL_NOT_FOUND(7001, "skill.notFound"),
    SKILL_INVALID_PACKAGE(7002, "skill.invalidPackage"),
    SKILL_FILE_TOO_LARGE(7003, "skill.fileTooLarge"),

    // Memory 8xxx
    MEMORY_NOT_FOUND(8001, "memory.notFound"),
    MEMORY_ACCESS_DENIED(8002, "memory.accessDenied"),

    // File 9xxx
    FILE_NOT_FOUND(9001, "file.notFound"),
    FILE_ALREADY_EXISTS(9002, "file.alreadyExists"),
    FILE_INVALID_FORMAT(9003, "file.invalidFormat"),

    // Security 10xxx
    DECRYPTION_FAILED(10001, "security.decryptionFailed");

    private final int code;
    private final String i18nKey;

    ErrorCode(int code, String i18nKey) {
        this.code = code;
        this.i18nKey = i18nKey;
    }
}
