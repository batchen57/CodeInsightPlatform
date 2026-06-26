package com.company.codeinsight.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI codeInsightOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Code Insight Platform API")
                        .description("API Documentation for Code Insight Platform MVP")
                        .version("v1.0.0"));
    }
}
