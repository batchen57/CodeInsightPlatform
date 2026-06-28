package com.company.codeinsight.common.auth;

/**
 * 当前请求线程的操作人上下文。
 *
 * <p>本类是 MVP 阶段的 placeholder，用于在 controller / service 层获取"当前是谁"，
 * 避免硬编码 'Admin' 字面值散落在业务代码里。后续接入 JWT / Spring Security 用户体系时，
 * 只需把 {@link com.company.codeinsight.common.config.SecurityConfig} 中的 OperatorHeaderFilter
 * 替换成从 {@code SecurityContextHolder} 读取即可，业务调用点（{@link #get()}）无需改动。</p>
 *
 * <p>典型用法：</p>
 * <pre>{@code
 *   String operator = OperatorContext.get();   // 拿到当前请求的操作人 username
 *   // 若未通过 filter 设置（如系统后台线程），fallback 为 "system"
 * }</pre>
 *
 * @see com.company.codeinsight.common.config.SecurityConfig#operatorHeaderFilter()
 */
public final class OperatorContext {

    /**
     * ThreadLocal 容器：保证同一请求线程内的 service / mapper 调用都能拿到一致的操作人。
     */
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    /**
     * 当线程未设置操作人时（如后台定时任务）的兜底值。
     */
    public static final String DEFAULT_OPERATOR = "system";

    private OperatorContext() {
        // 工具类，禁止实例化
    }

    /**
     * 在 filter / interceptor 入口设置当前请求的操作人。
     * 调用方负责在 finally 块里执行 {@link #clear()}，避免线程复用导致数据泄漏。
     */
    public static void set(String operator) {
        CURRENT.set(operator);
    }

    /**
     * 获取当前请求线程的操作人。
     *
     * @return 操作人 username；未设置时返回 {@link #DEFAULT_OPERATOR}（避免空指针）
     */
    public static String get() {
        String value = CURRENT.get();
        return (value == null || value.isBlank()) ? DEFAULT_OPERATOR : value;
    }

    /**
     * 清理 ThreadLocal，防止 Web 容器线程复用导致下一次请求误读。
     * 必须在 {@code finally} 中执行。
     */
    public static void clear() {
        CURRENT.remove();
    }
}