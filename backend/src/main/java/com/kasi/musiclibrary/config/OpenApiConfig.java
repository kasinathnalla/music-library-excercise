package com.kasi.musiclibrary.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
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

                                No third-party music service is involved and no credentials are needed.
                                The application is its own API and owns its catalog.
                                """)
                        .license(new License().name("Unlicensed exercise submission")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local")));
    }
}
