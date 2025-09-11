package com.split.ai.split.service.server.application;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication(scanBasePackages = "com.split.ai")
@EntityScan(basePackages = "com.split.ai.split.service.repository.entity")
@OpenAPIDefinition
public class SplitApplication {
    public static void main(String[] args) {
        SpringApplication.run(SplitApplication.class, args);
    }
}
