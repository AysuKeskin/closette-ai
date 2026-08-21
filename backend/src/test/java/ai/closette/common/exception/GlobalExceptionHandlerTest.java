package ai.closette.common.exception;

import ai.closette.common.api.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/** Every failure the client sees goes through here, so the envelope is a contract. */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @SuppressWarnings("unused")
    private void sample(String request) {
    }

    @Test
    void apiExceptionKeepsItsStatusCodeAndMessage() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleApi(ApiException.notFound("Item not found"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().error().code()).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(response.getBody().error().message()).isEqualTo("Item not found");
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
        Method method = getClass().getDeclaredMethod("sample", String.class);
        ResponseEntity<ApiResponse<Void>> response = handler.handleValidation(
                new MethodArgumentNotValidException(new MethodParameter(method, 0),
                        new BeanPropertyBindingResult(new Object(), "request")));

        assertThat(response.getBody().error().message()).isEqualTo("Invalid request");
    }

    @Test
    void accessDeniedIsReportedWithoutDetail() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleAccessDenied(new AccessDeniedException("wardrobe_items row 42"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().error().message()).doesNotContain("wardrobe_items");
    }

    @Test
    void unexpectedExceptionsNeverLeakInternals() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleUnexpected(new IllegalStateException("jdbc:postgresql://user:password@host/db"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().error().code()).isEqualTo(ErrorCode.INTERNAL);
        assertThat(response.getBody().error().message()).isEqualTo("Something went wrong");
    }
}
