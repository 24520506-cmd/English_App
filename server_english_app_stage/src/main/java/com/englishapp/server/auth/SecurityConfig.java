package com.englishapp.server.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean FilterRegistrationBean<BearerAuthFilter> bearerServletRegistration(BearerAuthFilter bearer) {
        FilterRegistrationBean<BearerAuthFilter> registration = new FilterRegistrationBean<>(bearer);
        registration.setEnabled(false); // Spring Security owns this filter's position.
        return registration;
    }

    @Bean FilterRegistrationBean<ApiRequestLoggingFilter> apiRequestLoggingServletRegistration(
            ApiRequestLoggingFilter requestLogger) {
        FilterRegistrationBean<ApiRequestLoggingFilter> registration = new FilterRegistrationBean<>(requestLogger);
        registration.setEnabled(false); // Spring Security owns this filter's position.
        return registration;
    }

    @Bean SecurityFilterChain filterChain(HttpSecurity http, BearerAuthFilter bearer,
                                          ApiRequestLoggingFilter requestLogger) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/users", "/api/v1/auth/login", "/api/v1/auth/refresh",
                                "/api/v1/auth/password-reset/**", "/api/v1/auth/google-link/callback",
                                "/api/v1/topics", "/api/v1/bands",
                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(bearer, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(requestLogger, BearerAuthFilter.class)
                .build();
    }
}
