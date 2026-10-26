package com.openlinklearn.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class ApiCorsConfiguration implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public ApiCorsConfiguration(
            @Value("${openlinklearn.cors.allowed-origin:http://localhost:4200}") String allowedOrigins
    ) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PATCH", "DELETE")
                .allowedHeaders("Content-Type")
                .allowCredentials(false)
                .maxAge(3600);
    }
}