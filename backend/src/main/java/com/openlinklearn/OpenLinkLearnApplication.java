package com.openlinklearn;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@OpenAPIDefinition(info = @Info(
    title = "Open Link Learn API",
    version = "v1",
    description = "API for organizing learning links and personal interests across languages, hobbies, and skills."
))
public class OpenLinkLearnApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenLinkLearnApplication.class, args);
    }
}