package com.company.codeinsight.common.util.jackson;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;

/**
 * Y/N 布尔反序列化器：与 {@link YnBooleanSerializer} 配对
 * <p>接受字符串与布尔两种形态，大小写不敏感：</p>
 * <ul>
 *   <li>{@code "Y"} / {@code "y"} / {@code "TRUE"} / {@code "true"} → {@code true}</li>
 *   <li>{@code "N"} / {@code "n"} / {@code "FALSE"} / {@code "false"} → {@code false}</li>
 *   <li>{@code null} / 空串 → {@code null}（由 DTO 字段类型 Boolean 接收；语义等同"未确认"）</li>
 * </ul>
 * <p>其它形态抛 {@link JsonParseException}，让前端能看到清晰的字段错误。</p>
 */
public class YnBooleanDeserializer extends StdDeserializer<Boolean> {

    private static final long serialVersionUID = 1L;

    public YnBooleanDeserializer() {
        super(Boolean.class);
    }

    @Override
    public Boolean deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        // 已经是 boolean 字面量（罕见但允许）
        if (p.getCurrentToken().isBoolean()) {
            return p.getBooleanValue();
        }
        if (p.getCurrentToken() == null || p.getCurrentToken() == JsonToken.VALUE_NULL) {
            return null;
        }
        String raw = p.getValueAsString();
        if (raw == null) return null;
        String v = raw.trim();
        if (v.isEmpty()) return null;
        String upper = v.toUpperCase();
        if ("Y".equals(upper) || "TRUE".equals(upper)) return Boolean.TRUE;
        if ("N".equals(upper) || "FALSE".equals(upper)) return Boolean.FALSE;
        throw new JsonParseException(p, "字段 " + p.getCurrentName() + " 必须是 Y/N 或 true/false，当前值：" + raw);
    }
}