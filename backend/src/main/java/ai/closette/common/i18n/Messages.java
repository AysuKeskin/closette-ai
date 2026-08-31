package ai.closette.common.i18n;

import ai.closette.config.MessageConfig;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Resolves user-facing text in the language of the current request.
 *
 * Anything a user reads goes through here — never a hard-coded sentence in a
 * service. Sentences are looked up whole rather than assembled from fragments,
 * because word order differs between English and Turkish.
 */
@Component
public class Messages {

    private final MessageSource messageSource;

    public Messages(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /** The request's language, narrowed to one we actually ship. */
    public static Locale currentLocale() {
        Locale locale = LocaleContextHolder.getLocale();
        return MessageConfig.TURKISH.getLanguage().equals(locale.getLanguage())
                ? MessageConfig.TURKISH
                : MessageConfig.ENGLISH;
    }

    /** The two-letter tag to hand to the AI service so its prose matches the UI. */
    public static String currentLanguageTag() {
        return currentLocale().getLanguage();
    }

    /** A missing key returns the key itself: visible in the UI, never a 500. */
    public String get(String key, Object... args) {
        return messageSource.getMessage(key, args, key, currentLocale());
    }

    /**
     * Count-aware lookup: {@code key.one} for exactly one, {@code key.other}
     * otherwise. Turkish keeps the noun singular after a numeral, so both forms
     * are usually identical there — the split exists for English.
     */
    public String plural(String key, long count, Object... args) {
        return get(key + (count == 1 ? ".one" : ".other"), args);
    }
}
