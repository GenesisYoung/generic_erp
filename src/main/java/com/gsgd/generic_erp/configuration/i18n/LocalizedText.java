package com.gsgd.generic_erp.configuration.i18n;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * A message key with its arguments, stored instead of finished text when the
 * text is shown later, possibly to someone using another language
 * (notifications). {@link #render(Locale)} produces the text.
 * <p>
 * An argument is one of:
 * <ul>
 * <li>a {@link String} or a {@link Number}: shown as-is, the same in every
 * language (order numbers, counts, ISO dates). Pass numbers as numbers so a
 * text can use {@code {0,choice,...}} for singular and plural;</li>
 * <li>another {@code LocalizedText}: rendered first, in the same language.</li>
 * </ul>
 * {@link #join(List)} builds a list ("A, B and C" style) whose separator also
 * follows the language.
 */
public record LocalizedText(String key, List<Object> args) {

    /** Marker key for {@link #join(List)}. */
    public static final String JOIN = "#join";

    public LocalizedText {
        List<Object> clean = new ArrayList<>();
        if (args != null) {
            for (Object a : args) {
                clean.add(normalize(a));
            }
        }
        args = List.copyOf(clean);
    }

    public static LocalizedText of(String key, Object... args) {
        return new LocalizedText(key, args == null ? List.of() : Arrays.asList(args));
    }

    /** The parts joined with the language's list separator ("、" or ", "). */
    public static LocalizedText join(List<?> parts) {
        return new LocalizedText(JOIN, new ArrayList<>(parts));
    }

    /** The text in {@code locale}. */
    public String render(Locale locale) {
        Object[] rendered = args.stream()
                .map(a -> a instanceof LocalizedText t ? t.render(locale) : a)
                .toArray();
        if (JOIN.equals(key)) {
            String separator = I18n.t(locale, "common.listSeparator");
            return String.join(separator, Arrays.stream(rendered).map(String::valueOf).toList());
        }
        return I18n.t(locale, key, rendered);
    }

    private static Object normalize(Object a) {
        if (a == null) {
            return "";
        }
        if (a instanceof LocalizedText || a instanceof String) {
            return a;
        }
        if (a instanceof Integer || a instanceof Long || a instanceof Short || a instanceof Byte) {
            return ((Number) a).longValue();
        }
        if (a instanceof Number n) {
            return n.doubleValue();
        }
        return String.valueOf(a); // dates, enums: their ISO / name form
    }
}
