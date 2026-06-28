package com.company.codeinsight.common.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 从 HTTP header 中读取当前操作人并写入 {@link OperatorContext}。
 *
 * <p>当前仅识别 {@code X-Operator} 请求头（前端在 MVP 阶段仍传 'Admin'，未来接 JWT 时替换）。</p>
 *
 * <p>约定：每个请求必须在 {@code finally} 里 clear，避免线程复用导致跨请求数据泄漏。</p>
 */
public class OperatorHeaderFilter extends OncePerRequestFilter {

    /**
     * 自定义请求头名称，与前端约定；后续接入 JWT 时可改为从 Authorization 解析。
     */
    public static final String OPERATOR_HEADER = "X-Operator";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String operator = request.getHeader(OPERATOR_HEADER);
        if (operator != null && !operator.isBlank()) {
            OperatorContext.set(operator.trim());
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            OperatorContext.clear();
        }
    }
}