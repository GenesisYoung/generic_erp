package com.gsgd.generic_erp.util;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import org.jspecify.annotations.Nullable;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import com.nimbusds.jose.shaded.gson.JsonParseException;

import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Component("jsonKeyGenerator")
public class JsonKeyGenerator implements KeyGenerator {
    private final ObjectMapper mapper;

    public JsonKeyGenerator() {
        this.mapper = JsonMapper.builder()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .build();
    }

    @Override
    public Object generate(Object target, Method method, @Nullable Object... params) {
        try {
            String payload = mapper.writeValueAsString(params);
            String digest = DigestUtils.md5DigestAsHex(payload.getBytes(StandardCharsets.UTF_8));
            return method.getDeclaringClass().getSimpleName() + "." + method.getName() + ":" + digest;
        } catch (JsonParseException e) {
            throw new IllegalStateException("Cannot build cache key for " + method, e);
        }
    }

}
