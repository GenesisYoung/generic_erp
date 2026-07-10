package com.gsgd.generic_erp.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GlobalVariable {

    @Value("${spring.global.variable.default-language}")
    private String DEFAULT_LANGUAGE;

    public String getDEFAULT_LANGUAGE() {
        return DEFAULT_LANGUAGE;
    }

}
