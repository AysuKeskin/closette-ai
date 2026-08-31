package ai.closette.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.List;
import java.util.Locale;

/**
 * Wires the two supported languages. The client picks one per request with an
 * {@code Accept-Language} header; anything else falls back to English.
 */
@Configuration
public class MessageConfig {

    public static final Locale ENGLISH = Locale.ENGLISH;
    public static final Locale TURKISH = Locale.forLanguageTag("tr");

    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        // Without this, an unmatched locale would fall back to whatever locale the
        // JVM happens to run in — the server's machine deciding the user's language.
        source.setFallbackToSystemLocale(false);
        source.setDefaultLocale(ENGLISH);
        // Spring skips MessageFormat for messages without arguments, which would
        // make apostrophe escaping mean different things depending on whether a
        // key happens to take a parameter — and leak a literal '' to the user.
        // Formatting everything makes the rule uniform: always escape ' as ''.
        source.setAlwaysUseMessageFormat(true);
        return source;
    }

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(List.of(ENGLISH, TURKISH));
        resolver.setDefaultLocale(ENGLISH);
        return resolver;
    }

    /**
     * Makes {@code @NotBlank(message = "{validation.name.required}")} resolve from
     * the same bundles, so form errors are translated like everything else.
     */
    @Bean
    public LocalValidatorFactoryBean validator(MessageSource messageSource) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setValidationMessageSource(messageSource);
        return validator;
    }
}
