package com.agile.paybot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Stateless — no server-side HTTP sessions
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // CSRF: disabled for stateless REST API + WebSocket (SockJS uses its own CSRF via STOMP headers)
                // TODO: Re-enable CSRF with CookieCsrfTokenRepository when adding user authentication
                .csrf(csrf -> csrf.disable())

                // CORS handled by CorsConfig bean
                .cors(cors -> {})

                // Authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers("/api/chat", "/api/health").permitAll()
                        // WebSocket endpoint
                        .requestMatchers("/ws-paybot/**").permitAll()
                        // Actuator health only
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // Block all other actuator endpoints
                        .requestMatchers("/actuator/**").denyAll()
                        // Deny everything else by default
                        .anyRequest().denyAll()
                )

                // Security headers
                .headers(headers -> headers
                        .xssProtection(xss -> xss
                                .headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                        .contentTypeOptions(cto -> {})
                        .frameOptions(frame -> frame.deny())
                );

        return http.build();
    }
}
