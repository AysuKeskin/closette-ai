package ai.closette.common.exception;

import ai.closette.common.api.ApiResponse;
import ai.closette.common.i18n.Messages;
import ai.closette.config.MessageConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every failure the client sees goes through here, so the envelope is a contract.
 * Runs against the real message bundles — a broken or missing translation fails
 * the test rather than reaching a user.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler(new Messages(new MessageConfig().messageSource()));

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    private void requestIn(Locale locale) {
        LocaleContextHolder.setLocale(locale);
    }

    @SuppressWarnings("unused")
    private void sample(String request) {
    }

    @Test
    void apiExceptionKeepsItsStatusCodeAndResolvesItsMessage() {
        requestIn(Locale.ENGLISH);

        ResponseEntity<ApiResponse<Void>> response = handler.handleApi(
                ApiException.notFound(MessageKeys.ITEM_NOT_FOUND));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().error().code()).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(response.getBody().error().message()).isEqualTo("Item not found");
    }

    @Test
    void theSameExceptionAnswersInTurkishForATurkishRequest() {
        requestIn(MessageConfig.TURKISH);

        ResponseEntity<ApiResponse<Void>> response = handler.handleApi(
                ApiException.notFound(MessageKeys.ITEM_NOT_FOUND));

        // The code is language-independent — the client branches on it, not the text.
        assertThat(response.getBody().error().code()).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(response.getBody().error().message()).isEqualTo("Parça bulunamadı");
    }

    @Test
    void anUnsupportedLanguageFallsBackToEnglish() {
        requestIn(Locale.GERMAN);

        ResponseEntity<ApiResponse<Void>> response = handler.handleApi(
                ApiException.notFound(MessageKeys.ITEM_NOT_FOUND));

        assertThat(response.getBody().error().message()).isEqualTo("Item not found");
    }

    @Test
    void messageArgumentsAreInterpolated() {
        requestIn(MessageConfig.TURKISH);

        ResponseEntity<ApiResponse<Void>> response = handler.handleApi(
                new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION,
                        MessageKeys.OUTFIT_AI_FALLBACK_RATIONALE, "akşam yemeği"));

        assertThat(response.getBody().error().message()).isEqualTo("akşam yemeği için eksiksiz bir kombin.");
    }

    @Test
    void fieldPrefixesSurviveTranslation() {
        // The mobile client splits on "email:" to put the message under the right
        // input, so the prefix must stay English in both bundles.
        requestIn(MessageConfig.TURKISH);

        ResponseEntity<ApiResponse<Void>> response = handler.handleApi(
                ApiException.conflict(MessageKeys.AUTH_EMAIL_TAKEN));

        assertThat(response.getBody().error().message())
                .startsWith("email: ")
                .isNotEqualTo("email: An account with this email already exists");
    }

    @Test
    void validationErrorsAreJoinedAsFieldPrefixedMessages() throws Exception {
        Method method = getClass().getDeclaredMethod("sample", String.class);
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "email", "must not be blank"));
        binding.addError(new FieldError("request", "password", "must not be blank"));

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidation(
                new MethodArgumentNotValidException(new MethodParameter(method, 0), binding));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error().code()).isEqualTo(ErrorCode.VALIDATION);
        // The mobile client splits this string back into per-field messages, so
        // the "field: message; field: message" shape must not drift.
        assertThat(response.getBody().error().message())
                .isEqualTo("email: must not be blank; password: must not be blank");
    }

    @Test
    void validationWithoutFieldErrorsStillHasAMessage() throws Exception {
        requestIn(MessageConfig.TURKISH);
        Method method = getClass().getDeclaredMethod("sample", String.class);

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidation(
                new MethodArgumentNotValidException(new MethodParameter(method, 0),
                        new BeanPropertyBindingResult(new Object(), "request")));

        assertThat(response.getBody().error().message()).isEqualTo("Geçersiz istek");
    }

    @Test
    void accessDeniedIsReportedWithoutDetail() {
        requestIn(Locale.ENGLISH);

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleAccessDenied(new AccessDeniedException("wardrobe_items row 42"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().error().message()).doesNotContain("wardrobe_items");
    }

    @Test
    void unexpectedExceptionsNeverLeakInternals() {
        requestIn(Locale.ENGLISH);

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleUnexpected(new IllegalStateException("jdbc:postgresql://user:password@host/db"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().error().code()).isEqualTo(ErrorCode.INTERNAL);
        assertThat(response.getBody().error().message()).isEqualTo("Something went wrong");
    }

    @Test
    void anUnknownKeyDegradesToTheKeyRatherThanA500() {
        requestIn(Locale.ENGLISH);

        ResponseEntity<ApiResponse<Void>> response =
                handler.handleApi(ApiException.notFound("error.key.thatDoesNotExist"));

        assertThat(response.getBody().error().message()).isEqualTo("error.key.thatDoesNotExist");
    }
}
