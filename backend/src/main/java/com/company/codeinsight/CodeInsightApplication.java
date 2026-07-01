package com.company.codeinsight;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableScheduling
@SpringBootApplication
public class CodeInsightApplication {
    public static void main(String[] args) {
        SpringApplication.run(CodeInsightApplication.class, args);
    }
}
