package com.gsgd.generic_erp.configuration.i18n;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

class I18nTest {

    private static final Locale ZH = Locale.SIMPLIFIED_CHINESE;
    private static final Locale EN = Locale.US;

    @AfterEach
    void reset() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void bothLanguagesHaveTheSameKeysAndEveryTextParses() throws Exception {
        Properties en = load("i18n/messages.properties");
        Properties zh = load("i18n/messages_zh_CN.properties");
        assertEquals(en.stringPropertyNames(), zh.stringPropertyNames());
        // A single apostrophe silently disappears under MessageFormat; it must be written ''.
        Pattern loneQuote = Pattern.compile("(?<!')'(?!')");
        for (Properties p : List.of(en, zh)) {
            for (String key : p.stringPropertyNames()) {
                String text = p.getProperty(key);
                assertDoesNotThrow(() -> new MessageFormat(text), key);
                assertTrue(!loneQuote.matcher(text).find(), key + " has a single apostrophe: " + text);
            }
        }
    }

    @Test
    void followsTheRequestLanguage() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("en-US"));
        assertEquals("Invalid username or password", I18n.t("auth.invalidCredentials"));
        LocaleContextHolder.setLocale(Locale.forLanguageTag("zh-CN"));
        assertEquals("用户名或密码无效", I18n.t("auth.invalidCredentials"));
        // Any English or Chinese variant maps to the supported one.
        LocaleContextHolder.setLocale(Locale.UK);
        assertEquals(EN, I18n.currentLocale());
    }

    @Test
    void noRequestMeansTheDefaultLanguage() {
        assertEquals(I18n.defaultLocale(), I18n.currentLocale());
    }

    @Test
    void argumentsAndNestedKeys() {
        assertEquals("File name is empty or too long",
                I18n.t(EN, "audit.error.fieldInvalid", I18n.ref("audit.field.fileName")));
        assertEquals("文件名不能为空或超长",
                I18n.t(ZH, "audit.error.fieldInvalid", I18n.ref("audit.field.fileName")));
    }

    @Test
    void unknownKeyComesBackUntouched() {
        String raw = "Can't parse {weird} text";
        assertEquals(raw, I18n.t(EN, raw));
    }

    @Test
    void localizedTextRendersPluralsListsAndSurvivesStorage() {
        LocalizedText summary = LocalizedText.of("followup.notify.more",
                LocalizedText.join(List.of(
                        LocalizedText.of("followup.notify.item", "PO-1", "2026-11-21"),
                        LocalizedText.of("followup.notify.item", "PO-2", "2026-11-22"))),
                3);
        LocalizedText content = LocalizedText.of("followup.notify.dueContent", summary);

        LocalizedTextConverter converter = new LocalizedTextConverter();
        LocalizedText stored = converter.convertToEntityAttribute(converter.convertToDatabaseColumn(content));
        assertEquals(content, stored);

        assertEquals("Please follow up on: PO-1 (by 2026-11-21), PO-2 (by 2026-11-22) and 3 more",
                stored.render(EN));
        assertEquals("请跟进：PO-1（2026-11-21 前）、PO-2（2026-11-22 前） 等，另有 3 项",
                stored.render(ZH));
        assertEquals("1 follow-up is due", LocalizedText.of("followup.notify.dueTitle", 1).render(EN));
        assertEquals("4 follow-ups are due", LocalizedText.of("followup.notify.dueTitle", 4).render(EN));
    }

    private static Properties load(String path) throws Exception {
        Properties p = new Properties();
        try (var in = I18nTest.class.getClassLoader().getResourceAsStream(path)) {
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return p;
    }
}
