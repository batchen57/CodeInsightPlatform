package com.company.codeinsight.modules.entrypoint.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * EntryPointConfig JSON 编解码工具
 * 序列化失败一律回退 null/空对象（走默认行为），保证任务创建链路不会因 JSON 异常而失败。
 */
public final class EntryPointConfigCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<EntryPointConfig> TYPE = new TypeReference<>() {};

    private EntryPointConfigCodec() {
    }

    public static String encode(EntryPointConfig config) {
        if (config == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(config);
        } catch (Exception e) {
            return null;
        }
    }

    public static EntryPointConfig decode(String json) {
        if (json == null || json.isBlank()) {
            return new EntryPointConfig();
        }
        try {
            return MAPPER.readValue(json, TYPE);
        } catch (Exception e) {
            return new EntryPointConfig();
        }
    }
}