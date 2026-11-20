package com.gsgd.generic_erp.configuration.i18n;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Stores a {@link LocalizedText} as JSON: {@code {"key":"...","args":[...]}},
 * where an argument is a string, a number or another such object.
 */
@Converter
public class LocalizedTextConverter implements AttributeConverter<LocalizedText, String> {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Override
    public String convertToDatabaseColumn(LocalizedText text) {
        return text == null ? null : JSON.writeValueAsString(toNode(text));
    }

    @Override
    public LocalizedText convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return fromNode(JSON.readTree(json));
    }

    private static ObjectNode toNode(LocalizedText text) {
        ObjectNode node = JSON.createObjectNode();
        node.put("key", text.key());
        ArrayNode args = node.putArray("args");
        for (Object a : text.args()) {
            if (a instanceof LocalizedText t) {
                args.add(toNode(t));
            } else if (a instanceof Long l) {
                args.add(l);
            } else if (a instanceof Double d) {
                args.add(d);
            } else {
                args.add(String.valueOf(a));
            }
        }
        return node;
    }

    private static LocalizedText fromNode(JsonNode node) {
        List<Object> args = new ArrayList<>();
        JsonNode list = node.get("args");
        if (list != null) {
            for (JsonNode a : list) {
                if (a.isObject()) {
                    args.add(fromNode(a));
                } else if (a.isIntegralNumber()) {
                    args.add(a.longValue());
                } else if (a.isNumber()) {
                    args.add(a.doubleValue());
                } else {
                    args.add(a.asString());
                }
            }
        }
        return new LocalizedText(node.get("key").asString(), args);
    }
}
