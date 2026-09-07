package com.kasi.musiclibrary.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI musicLibraryOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Music Library API")
                        .version("1.0.0")
                        .description("""
                                Manages a music library: ingesting audio files, reading their embedded
                                tags into first-class artist and album entities, searching the catalog,
                                and streaming audio with range-request support.

                                No third-party music service is involved. The application is its own
                                API and owns its catalog.

                                Every endpoint except this documentation and the health check needs
                                an account. Sign in with HTTP Basic using the Authorize button; the
                                seeded accounts are admin/admin and customer/customer. An admin may
                                upload, edit, and delete. A customer may browse, search, and stream.
                                """)
                        .license(new License().name("Unlicensed exercise submission")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local")))
                // Declared so Swagger UI's Authorize button works, which is what keeps UC-7
                // -- understand the API without running anything -- true now that the
                // endpoints need credentials.
                .components(new Components().addSecuritySchemes("basicAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("basic")
                                .description("Seeded accounts: admin/admin, customer/customer")))
                .addSecurityItem(new SecurityRequirement().addList("basicAuth"));
    }
}
