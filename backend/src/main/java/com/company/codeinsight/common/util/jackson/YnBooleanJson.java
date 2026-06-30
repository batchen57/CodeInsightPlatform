package com.company.codeinsight.common.util.jackson;

/**
 * Y/N 布尔 JSON 编解码：DTO 字段在 JSON 中以 "Y" / "N" 呈现，Java 侧仍用 {@link Boolean}。
 */
public final class YnBooleanJson {

    private YnBooleanJson() {
    }

    public static String format(Boolean value) {
        return Boolean.TRUE.equals(value) ? "Y" : "N";
    }

    public static Boolean parse(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim();
        if (v.isEmpty()) {
            return null;
        }
        String upper = v.toUpperCase();
        if ("Y".equals(upper) || "TRUE".equals(upper)) {
            return Boolean.TRUE;
        }
        if ("N".equals(upper) || "FALSE".equals(upper)) {
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("字段必须是 Y/N 或 true/false，当前值：" + raw);
    }
}
