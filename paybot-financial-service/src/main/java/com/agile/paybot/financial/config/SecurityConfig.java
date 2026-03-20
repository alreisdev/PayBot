package com.agile.paybot.financial.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Stateless — internal service, no sessions
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // CSRF disabled — internal service not exposed to browsers
                .csrf(csrf -> csrf.disable())

                // Authorization rules — internal service, only called by AI service
                .authorizeHttpRequests(auth -> auth
                        // Internal API endpoints (called by AI service via Feign)
                        .requestMatchers("/api/internal/**").permitAll()
                        // Actuator health for Docker healthcheck
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        // Swagger UI (controlled by application properties, disabled in docker profile)
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // Block all other actuator endpoints
                        .requestMatchers("/actuator/**").denyAll()
                        // Deny everything else
                        .anyRequest().denyAll()
                )

                // Security headers
                .headers(headers -> headers
                        .contentTypeOptions(cto -> {})
                        .frameOptions(frame -> frame.deny())
                );

        return http.build();
    }
}
