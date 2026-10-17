package com.example.DocuMindAI.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI documentAssistantOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Document Assistant API")
                        .description("REST APIs for document processing")
                        .version("v1.0.0"));
    }
}
