package com.gsgd.generic_erp.pmc;

import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Component
public class AuditJson {
    private final JsonMapper mapper = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(tools.jackson.databind.MapperFeature.ALLOW_COERCION_OF_SCALARS).build();

    public String write(Object value) {
        return mapper.writeValueAsString(value);
    }

    public <T> T read(String value, Class<T> type) {
        try {
            return mapper.readValue(value, type);
        } catch (Exception e) {
            throw new AuditException(400, "INVALID_BODY", "请求字段、类型或带时区时间格式不合法");
        }
    }
}
