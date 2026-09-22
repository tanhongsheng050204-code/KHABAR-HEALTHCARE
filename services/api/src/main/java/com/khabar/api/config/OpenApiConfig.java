package com.khabar.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** The browsable API reference at /docs. Fictional data only; the demo tokens come from /dev/token. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI khabarApi() {
        return new OpenAPI()
                // Relative, so "Try it out" calls whichever address the page was opened on
                .servers(List.of(new Server().url("/")))
                .info(new Info()
                        .title("Khabar clinical API")
                        .version("0.1.0")
                        .description("""
                                The clinic's API: sign-in and roles, patients, "what I take", the pre-visit page, visits with
                                the safety checks, summaries, follow-up and home readings.

                                To try an endpoint on the demo: POST /dev/token?as=doctor (or patient, or caregiver), copy the
                                token, press Authorize, and paste it. Everything in the demo is fictional."""))
                .components(new Components().addSecuritySchemes("bearer", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("A Supabase sign-in token, or a demo token from POST /dev/token")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
