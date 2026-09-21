package ai.closette.common.exception;

/**
 * Keys into the message bundles. Constants rather than inline strings so a typo
 * is a compile error instead of a key leaking into the UI at runtime.
 */
public final class MessageKeys {

    // ---- Auth ----
    public static final String AUTH_REQUIRED = "error.auth.required";
    public static final String AUTH_INVALID_CREDENTIALS = "error.auth.invalidCredentials";
    public static final String AUTH_INVALID_REFRESH_TOKEN = "error.auth.invalidRefreshToken";
    public static final String AUTH_EMAIL_TAKEN = "error.auth.emailTaken";
    public static final String AUTH_USERNAME_TAKEN = "error.auth.usernameTaken";
    public static final String AUTH_EMAIL_ALREADY_VERIFIED = "error.auth.emailAlreadyVerified";
    public static final String AUTH_CODE_EXPIRED = "error.auth.codeExpired";
    public static final String AUTH_CODE_INCORRECT = "error.auth.codeIncorrect";
    public static final String AUTH_TOO_MANY_ATTEMPTS = "error.auth.tooManyAttempts";
    public static final String AUTH_RESEND_COOLDOWN = "error.auth.resendCooldown";
    public static final String RATE_LIMIT_EXCEEDED = "error.rateLimitExceeded";
    public static final String TOO_MANY_REQUESTS = "error.tooManyRequests";
    public static final String AUTH_EMAIL_NOT_VERIFIED = "error.auth.emailNotVerified";

    // ---- Not found ----
    public static final String USER_NOT_FOUND = "error.user.notFound";
    public static final String ITEM_NOT_FOUND = "error.item.notFound";
    public static final String OUTFIT_NOT_FOUND = "error.outfit.notFound";
    public static final String BEAUTY_NOT_FOUND = "error.beauty.notFound";
    public static final String WISHLIST_NOT_FOUND = "error.wishlist.notFound";

    // ---- Images and storage ----
    public static final String IMAGE_REQUIRED = "error.image.required";
    public static final String IMAGE_UNREADABLE = "error.image.unreadable";
    public static final String STORAGE_UPLOAD_FAILED = "error.storage.uploadFailed";

    // ---- AI ----
    public static final String AI_CONSENT_REQUIRED = "error.ai.consentRequired";
    public static final String INGREDIENT_REQUIRED = "error.ingredient.required";
    public static final String AI_UNAVAILABLE = "error.ai.unavailable";
    public static final String AI_UNAVAILABLE_ADD_MANUALLY = "error.ai.unavailableAddManually";

    // ---- Generic ----
    public static final String FORBIDDEN = "error.forbidden";
    public static final String INTERNAL = "error.internal";
    public static final String INVALID_REQUEST = "error.validation.invalidRequest";

    // ---- Should I buy this? ----
    public static final String BUY_UNREADABLE = "buy.unreadable";
    public static final String BUY_WARDROBE_EMPTY = "buy.wardrobeEmpty";
    public static final String BUY_NO_TAGS = "buy.noTags";
    public static final String BUY_FIT_GOOD = "buy.fit.good";
    public static final String BUY_FIT_PARTIAL = "buy.fit.partial";
    public static final String BUY_FIT_LOW = "buy.fit.low";
    public static final String BUY_PAIRS_WITH = "buy.pairsWith";
    public static final String BUY_ALREADY_OWN = "buy.alreadyOwn";
    public static final String BUY_LABEL_FALLBACK = "buy.label.fallback";

    // ---- Get Ready ----
    public static final String OUTFIT_FIRST_LOOK_TITLE = "outfit.firstLook.title";
    public static final String OUTFIT_FIRST_LOOK_RATIONALE = "outfit.firstLook.rationale";
    public static final String OUTFIT_DEFAULT_TITLE = "outfit.defaultTitle";
    public static final String OUTFIT_DEFAULT_OCCASION = "outfit.defaultOccasion";
    public static final String OUTFIT_RETRY_TITLE = "outfit.retryTitle";
    public static final String OUTFIT_UNCLEAR_OCCASION = "outfit.unclearOccasion";
    public static final String OUTFIT_RULE_BASED_TITLE = "outfit.ruleBased.title";
    public static final String OUTFIT_RULE_BASED_RATIONALE = "outfit.ruleBased.rationale";
    public static final String OUTFIT_AI_FALLBACK_RATIONALE = "outfit.aiFallbackRationale";
    public static final String OUTFIT_COLOR_SEASON_HINT = "outfit.colorSeasonHint";

    // ---- Emails ----
    public static final String EMAIL_VERIFY_SUBJECT = "email.verify.subject";
    public static final String EMAIL_VERIFY_GREETING = "email.verify.greeting";
    public static final String EMAIL_VERIFY_INTRO = "email.verify.intro";
    public static final String EMAIL_RESET_SUBJECT = "email.reset.subject";
    public static final String EMAIL_RESET_INTRO = "email.reset.intro";
    public static final String EMAIL_RESET_CODE = "email.reset.code";
    public static final String EMAIL_RESET_IGNORE = "email.reset.ignore";
    public static final String EMAIL_EXPIRY = "email.expiry";

    /** Wardrobe category as a word inside a sentence; suffixed .one/.other. */
    public static final String CATEGORY_PREFIX = "category.";
    public static final String CATEGORY_GENERIC = "category.generic";

    private MessageKeys() {
    }
}
