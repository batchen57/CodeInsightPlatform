package com.company.codeinsight.common.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.codeinsight.modules.auth.entity.UserAccount;
import com.company.codeinsight.modules.auth.mapper.UserAccountMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 从 HTTP header 中读取当前操作人并写入 {@link OperatorContext}。
 *
 * <p>当前识别 {@code X-Operator} 请求头（前端在 MVP 阶段仍传 'Admin'）；
 * 按 username 查 {@code ci_user} 表拿到 id/role，5 分钟缓存以避免每请求查表。</p>
 *
 * <p>约定：每个请求必须在 {@code finally} 里 clear，避免线程复用导致跨请求数据泄漏。</p>
 */
public class OperatorHeaderFilter extends OncePerRequestFilter {

    /**
     * 自定义请求头名称，与前端约定；后续接入 JWT 时可改为从 Authorization 解析。
     */
    public static final String OPERATOR_HEADER = "X-Operator";

    /** username → (userId, role) 缓存 5 分钟，防止每请求查库。 */
    private static final ConcurrentMap<String, CachedUser> USER_CACHE = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 5L * 60 * 1000;

    private final UserAccountMapper userAccountMapper;

    public OperatorHeaderFilter() {
        this.userAccountMapper = null;
    }

    public OperatorHeaderFilter(UserAccountMapper userAccountMapper) {
        this.userAccountMapper = userAccountMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String operator = request.getHeader(OPERATOR_HEADER);
        if (operator != null && !operator.isBlank()) {
            String username = operator.trim();
            ResolvedUser resolved = resolveUser(username);
            OperatorContext.set(resolved.username, resolved.userId, resolved.role);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            OperatorContext.clear();
        }
    }

    /**
     * 解析 username → (userId, role)。带 5 分钟缓存；未命中时回退 admin。
     */
    private ResolvedUser resolveUser(String username) {
        long now = System.currentTimeMillis();
        CachedUser cached = USER_CACHE.get(username);
        if (cached != null && now - cached.cachedAt < CACHE_TTL_MS) {
            return new ResolvedUser(username, cached.userId, cached.role);
        }
        Long userId = OperatorContext.DEFAULT_USER_ID;
        String role = "ADMIN";
        if (userAccountMapper != null) {
            try {
                UserAccount account = userAccountMapper.selectOne(
                        new LambdaQueryWrapper<UserAccount>()
                                .eq(UserAccount::getUsername, username)
                                .last("LIMIT 1"));
                if (account != null) {
                    if (account.getId() != null) userId = account.getId();
                    if (account.getRole() != null && !account.getRole().isBlank()) role = account.getRole();
                }
            } catch (Exception ignored) {
                // ci_user 表未初始化或查询失败时回退 admin，避免阻塞请求
            }
        }
        USER_CACHE.put(username, new CachedUser(userId, role, now));
        return new ResolvedUser(username, userId, role);
    }

    private record ResolvedUser(String username, Long userId, String role) {}

    private record CachedUser(Long userId, String role, long cachedAt) {}
}
