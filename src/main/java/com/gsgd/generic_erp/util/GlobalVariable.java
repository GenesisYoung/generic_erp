package com.gsgd.generic_erp.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Application-wide configuration values injected from properties.
 * Currently exposes the default response language ({@code EN} / {@code CN}),
 * sourced from the {@code LANGUAGE} environment variable.
 */
@Component
public class GlobalVariable {

    @Value("${spring.global.variable.default-language}")
    private String DEFAULT_LANGUAGE;

    public String getDEFAULT_LANGUAGE() {
        return DEFAULT_LANGUAGE;
    }

}
