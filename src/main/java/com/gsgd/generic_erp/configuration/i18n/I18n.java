package com.gsgd.generic_erp.configuration.i18n;

import java.util.Locale;

import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * Looks up user-facing texts by key in the caller's language. Every message
 * the backend sends to a person goes through here; code never builds a
 * sentence itself.
 * <p>
 * Texts live in {@code src/main/resources/i18n/messages.properties} (English)
 * and {@code messages_zh_CN.properties} (Simplified Chinese). Add every new
 * key to both files.
 * <p>
 * Inside an HTTP request the language is the one the frontend sent in
 * {@code Accept-Language} (see {@link I18nConfig}); elsewhere (scheduled
 * jobs) pass a locale explicitly, usually from {@link UserLocaleRegistry}. An
 * unknown key is returned as-is, so a missing translation is visible but never
 * breaks a response.
 * <p>
 * This is a static helper on purpose: it works the same in services, error
 * handlers and plain unit tests, without changing any constructor.
 */
public final class I18n {

    private static final ResourceBundleMessageSource SOURCE = new ResourceBundleMessageSource();
    private static volatile Locale defaultLocale = Locale.SIMPLIFIED_CHINESE;

    static {
        SOURCE.setBasename("i18n/messages");
        SOURCE.setDefaultEncoding("UTF-8");
        SOURCE.setFallbackToSystemLocale(false);
        // A missing key comes back as null and t() returns the key untouched (not run
        // through MessageFormat), so a plain sentence passed as a key survives as-is.
        SOURCE.setUseCodeAsDefaultMessage(false);
        // Always parse with MessageFormat, so an apostrophe is written '' in every text.
        SOURCE.setAlwaysUseMessageFormat(true);
    }

    private I18n() {
    }

    /** The text for {@code key} in the current request's language. */
    public static String t(String key, Object... args) {
        return t(currentLocale(), key, args);
    }

    /** The text for {@code key} in {@code locale} (normalized to a supported language). */
    public static String t(Locale locale, String key, Object... args) {
        if (key == null) {
            return null;
        }
        String text = SOURCE.getMessage(key, args, null, supported(locale));
        return text != null ? text : key;
    }

    /** Renders a stored {@link LocalizedText}; null stays null. */
    public static String t(Locale locale, LocalizedText text) {
        return text == null ? null : text.render(supported(locale));
    }

    /**
     * A message argument that is itself a key, e.g. a field name inside an
     * error message: {@code t("audit.error.fieldInvalid", I18n.ref("audit.field.orderNumber"))}.
     */
    public static MessageSourceResolvable ref(String key, Object... args) {
        return new DefaultMessageSourceResolvable(new String[] { key }, args, key);
    }

    /** The current request's language, normalized to a supported one. */
    public static Locale currentLocale() {
        // Outside a request (scheduled jobs) there is no locale context: use the
        // configured default, not the server's operating-system locale.
        return LocaleContextHolder.getLocaleContext() == null
                ? defaultLocale
                : supported(LocaleContextHolder.getLocale());
    }

    public static Locale defaultLocale() {
        return defaultLocale;
    }

    static void setDefaultLocale(Locale locale) {
        defaultLocale = supported(locale, Locale.SIMPLIFIED_CHINESE);
    }

    /** Maps any locale to zh-CN or en-US by language; anything else becomes the default. */
    public static Locale supported(Locale locale) {
        return supported(locale, defaultLocale);
    }

    private static Locale supported(Locale locale, Locale fallback) {
        if (locale != null) {
            for (Locale s : I18nConfig.SUPPORTED) {
                if (s.getLanguage().equals(locale.getLanguage())) {
                    return s;
                }
            }
        }
        return fallback;
    }
}
