package com.gsgd.generic_erp.configuration.i18n;

import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * Localization: every response is written in the language the frontend asks
 * for in its {@code Accept-Language} header (zh-CN or en-US). Message texts live
 * in {@code src/main/resources/i18n/messages*.properties}; code uses
 * {@link I18n} with a key, never a hard-coded sentence.
 * <p>
 * When the header is missing or names an unsupported language,
 * {@code erp.i18n.default-locale} (zh-CN) is used.
 */
@Configuration
public class I18nConfig implements WebMvcConfigurer {

    /** Languages the backend has message files for. The first one is used when nothing else matches. */
    public static final List<Locale> SUPPORTED = List.of(Locale.SIMPLIFIED_CHINESE, Locale.US);

    private final UserLocaleRegistry userLocales;

    public I18nConfig(UserLocaleRegistry userLocales) {
        this.userLocales = userLocales;
    }

    @Bean
    public LocaleResolver localeResolver(@Value("${erp.i18n.default-locale:zh-CN}") String defaultLocale) {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(SUPPORTED);
        Locale fallback = Locale.forLanguageTag(defaultLocale);
        I18n.setDefaultLocale(fallback);
        resolver.setDefaultLocale(I18n.defaultLocale());
        return resolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Remember each signed-in user's language, so notifications created
        // later by scheduled jobs are written in that language too.
        registry.addInterceptor(new UserLocaleInterceptor(userLocales));
    }
}
