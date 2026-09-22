package com.khabar.api.config;

import com.khabar.api.crypto.FieldEncryptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Supabase puts "authenticated" in the aud claim of every signed-in user's token. */
    private static final String SUPABASE_AUDIENCE = "authenticated";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/api/health", "/actuator/health", "/error", "/dev/**", "/api/webhooks/**").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));
        return http.build();
    }

    /**
     * Verifies Supabase access tokens. New Supabase projects sign with asymmetric keys
     * published at .../auth/v1/.well-known/jwks.json (ES256 or RS256); older projects
     * use a shared HS256 secret. Set exactly one of SUPABASE_JWKS_URL or SUPABASE_JWT_SECRET.
     */
    @Bean
    public JwtDecoder jwtDecoder(@Value("${khabar.security.supabase-jwks-url:}") String jwksUrl,
                                 @Value("${khabar.security.supabase-jwt-secret:}") String secret) {
        NimbusJwtDecoder decoder;
        if (!jwksUrl.isBlank()) {
            decoder = NimbusJwtDecoder.withJwkSetUri(jwksUrl)
                    .jwsAlgorithm(SignatureAlgorithm.ES256)
                    .jwsAlgorithm(SignatureAlgorithm.RS256)
                    .build();
        } else if (secret.length() >= 32) {
            decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                    .macAlgorithm(MacAlgorithm.HS256)
                    .build();
        } else {
            throw new IllegalStateException("Set SUPABASE_JWKS_URL (asymmetric keys) or SUPABASE_JWT_SECRET (legacy, 32+ characters) so sign-ins can be verified.");
        }
        JwtClaimValidator<List<String>> audience = new JwtClaimValidator<>("aud", aud -> aud != null && aud.contains(SUPABASE_AUDIENCE));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(JwtValidators.createDefault(), audience));
        return decoder;
    }

    @Bean
    public FieldEncryptor fieldEncryptor(@Value("${khabar.security.field-encryption-key:}") String key) {
        return new FieldEncryptor(key);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${khabar.web.allowed-origins:http://localhost:3000}") String origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        source.registerCorsConfiguration("/dev/**", config);
        return source;
    }
}
