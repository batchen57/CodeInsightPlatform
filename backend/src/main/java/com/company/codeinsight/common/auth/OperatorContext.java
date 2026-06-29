package com.company.codeinsight.common.auth;

/**
 * 当前请求线程的操作人上下文。
 *
 * <p>本类是 MVP 阶段的 placeholder，用于在 controller / service 层获取"当前是谁"，
 * 避免硬编码 'Admin' 字面值散落在业务代码里。后续接入 JWT / Spring Security 用户体系时，
 * 只需把 {@link com.company.codeinsight.common.config.SecurityConfig} 中的 OperatorHeaderFilter
 * 替换成从 {@code SecurityContextHolder} 读取即可，业务调用点（{@link #get()} / {@link #getUserId()} 等）无需改动。</p>
 *
 * <p>典型用法：</p>
 * <pre>{@code
 *   String operator = OperatorContext.get();    // 拿到当前请求的操作人 username
 *   Long userId     = OperatorContext.getUserId();
 *   String role     = OperatorContext.getRole();
 * }</pre>
 *
 * <p>ThreadLocal 中存放的是 {@link Snapshot} record，3 个字段一起绑定；
 * 外部通过静态 getter 方法访问，避免空指针。</p>
 *
 * @see com.company.codeinsight.common.config.SecurityConfig#operatorHeaderFilter()
 */
public final class OperatorContext {

    /**
     * 当前请求线程绑定的操作人三元组（username + userId + role）。
     */
    public record Snapshot(String username, Long userId, String role) {}

    /**
     * ThreadLocal 容器：保证同一请求线程内的 service / mapper 调用都能拿到一致的操作人。
     */
    private static final ThreadLocal<Snapshot> CURRENT = new ThreadLocal<>();

    /**
     * 当线程未设置操作人时（如后台定时任务）的兜底 username。
     */
    public static final String DEFAULT_OPERATOR = "system";

    /**
     * 当线程未设置操作人时的兜底 userId（指向 ci_user.id=1，即 admin）。
     */
    public static final Long DEFAULT_USER_ID = 1L;

    /**
     * 当线程未设置操作人时的兜底 role。
     */
    public static final String DEFAULT_ROLE = "USER";

    private OperatorContext() {
        // 工具类，禁止实例化
    }

    /**
     * 在 filter / interceptor 入口设置当前请求的操作人（仅 username）。
     * 适用于未关联业务用户的内部调用；userId/role 走默认 ADMIN。
     */
    public static void set(String operator) {
        set(operator, DEFAULT_USER_ID, "ADMIN");
    }

    /**
     * 在 filter / interceptor 入口设置完整三元组。
     * 调用方负责在 finally 块里执行 {@link #clear()}，避免线程复用导致数据泄漏。
     */
    public static void set(String username, Long userId, String role) {
        CURRENT.set(new Snapshot(username, userId, role));
    }

    /**
     * 获取当前请求线程的操作人 username。
     *
     * @return 操作人 username；未设置时返回 {@link #DEFAULT_OPERATOR}（避免空指针）
     */
    public static String get() {
        Snapshot s = CURRENT.get();
        String v = s == null ? null : s.username();
        return (v == null || v.isBlank()) ? DEFAULT_OPERATOR : v;
    }

    /**
     * 获取当前请求线程的操作人 userId。
     *
     * @return ci_user.id；未设置时返回 {@link #DEFAULT_USER_ID}（admin）
     */
    public static Long getUserId() {
        Snapshot s = CURRENT.get();
        return s == null || s.userId() == null ? DEFAULT_USER_ID : s.userId();
    }

    /**
     * 获取当前请求线程的操作人 role。
     *
     * @return ADMIN / USER；未设置时返回 {@link #DEFAULT_ROLE}
     */
    public static String getRole() {
        Snapshot s = CURRENT.get();
        String r = s == null ? null : s.role();
        return (r == null || r.isBlank()) ? DEFAULT_ROLE : r;
    }

    /**
     * 清理 ThreadLocal，防止 Web 容器线程复用导致下一次请求误读。
     * 必须在 {@code finally} 中执行。
     */
    public static void clear() {
        CURRENT.remove();
    }
}
