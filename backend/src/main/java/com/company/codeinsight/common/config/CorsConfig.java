package com.company.codeinsight.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * 全局跨域资源共享 (CORS) 配置类
 * 允许前端本地开发环境及不同域名下的客户端与后端接口进行正常通信。
 */
@Configuration
public class CorsConfig {

    /**
     * 配置跨域过滤器 Bean
     * 允许所有的请求来源、请求头和 HTTP 方法，并支持凭证（如 Cookie）的安全传输。
     */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        
        // 允许所有 Origin 来源模式匹配（如 http://localhost:5173）
        config.addAllowedOriginPattern("*");
        
        // 允许所有的 HTTP 请求方法 (GET, POST, PUT, DELETE, OPTIONS等)
        config.addAllowedMethod("*");
        
        // 允许所有的自定义或标准 HTTP 请求头 (如 Authorization, Content-Type)
        config.addAllowedHeader("*");
        
        // 允许客户端请求携带凭证（如 Cookie 或认证 Header）
        config.setAllowCredentials(true);

        // 注册对所有路径 ("/**") 应用此跨域配置
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        
        return new CorsFilter(source);
    }
}

