package com.company.codeinsight.common.util.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

/**
 * Y/N 布尔序列化器：把 {@code Boolean} 字段序列化为单字符字符串
 * <ul>
 *   <li>{@code true}  → {@code "Y"}</li>
 *   <li>{@code false} / {@code null} → {@code "N"}</li>
 * </ul>
 * <p>与 {@link YnBooleanDeserializer} 配对使用，专用于"人工复核标记"等希望人在 JSON 视图中
 * 一眼看出是否确认的字段；其它布尔字段不要套用此序列化器。</p>
 */
public class YnBooleanSerializer extends StdSerializer<Boolean> {

    private static final long serialVersionUID = 1L;

    public YnBooleanSerializer() {
        super(Boolean.class);
    }

    @Override
    public void serialize(Boolean value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeString(Boolean.TRUE.equals(value) ? "Y" : "N");
    }
}