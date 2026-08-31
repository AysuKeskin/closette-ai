package ai.closette.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Application-level exception carrying an HTTP status plus a stable {@link ErrorCode}.
 *
 * The "message" is a message-bundle key, not a sentence — {@link GlobalExceptionHandler}
 * resolves it in the caller's language. Throw sites therefore read
 * {@code ApiException.notFound(MessageKeys.ITEM_NOT_FOUND)}.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final transient Object[] args;

    public ApiException(HttpStatus status, String code, String messageKey, Object... args) {
        super(messageKey);
        this.status = status;
        this.code = code;
        this.args = args;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    /** The bundle key. Same value as {@link #getMessage()}, named for what it is. */
    public String getMessageKey() {
        return getMessage();
    }

    public Object[] getArgs() {
        return args;
    }

    public static ApiException notFound(String messageKey, Object... args) {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, messageKey, args);
    }

    public static ApiException conflict(String messageKey, Object... args) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, messageKey, args);
    }

    public static ApiException unauthorized(String messageKey, Object... args) {
        return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, messageKey, args);
    }

    public static ApiException forbidden(String messageKey, Object... args) {
        return new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, messageKey, args);
    }

    public static ApiException emailNotVerified(String messageKey, Object... args) {
        return new ApiException(HttpStatus.FORBIDDEN, ErrorCode.EMAIL_NOT_VERIFIED, messageKey, args);
    }

    public static ApiException aiUnavailable(String messageKey, Object... args) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.AI_UNAVAILABLE, messageKey, args);
    }

    public static ApiException storage(String messageKey, Object... args) {
        return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.STORAGE_ERROR, messageKey, args);
    }

    public static ApiException validation(String messageKey, Object... args) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION, messageKey, args);
    }

    public static ApiException rateLimited(String messageKey, Object... args) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMITED, messageKey, args);
    }
}
