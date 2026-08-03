package com.gsgd.generic_erp.enums;

/**
 * Common contract for localized message enums ({@code Language_EN},
 * {@code Language_CN}); the active language is chosen at runtime from
 * {@code GlobalVariable}.
 */
public interface Language {

    /** Returns the message text in this language. */
    String getMessage();
}
