package com.gsgd.generic_erp.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Language {

    @Value("${app.language}")
    private String language;

    public String getLanguage() {
        return language;
    }
}
