package ai.closette.common.exception;

/**
 * Stable machine-readable error codes. The mobile client branches on these
 * (e.g. AI_UNAVAILABLE → "add manually" fallback) rather than on messages.
 */
public final class ErrorCode {

    public static final String VALIDATION = "VALIDATION";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String CONFLICT = "CONFLICT";
    public static final String EMAIL_NOT_VERIFIED = "EMAIL_NOT_VERIFIED";
    public static final String AI_UNAVAILABLE = "AI_UNAVAILABLE";
    public static final String STORAGE_ERROR = "STORAGE_ERROR";
    public static final String RATE_LIMITED = "RATE_LIMITED";
    public static final String INTERNAL = "INTERNAL";

    private ErrorCode() {
    }
}
