package ru.ticketpoint.backend.config;

import tools.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import java.util.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;
import ru.ticketpoint.backend.error.RestErrors;
import ru.ticketpoint.backend.security.JwtFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain chain(HttpSecurity http, JwtFilter filter, ObjectMapper json) {
        http.csrf(org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        a ->
                                a.requestMatchers(
                                                "/",
                                                "/index.html",
                                                "/app.js",
                                                "/style.css",
                                                "/favicon.ico",
                                                "/actuator/health")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/api/bookings", "/api/bookings/{id}",
                                                "/api/events", "/api/analytics/summary")
                                        .permitAll()
                                        .requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/register")
                                        .permitAll()
                                        .requestMatchers(
                                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/api/admin/**")
                                        .hasRole("ADMIN")
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(
                        e ->
                                e.authenticationEntryPoint(
                                                (req, res, ex) -> {
                                                    res.setStatus(401);
                                                    res.setContentType("application/json;charset=UTF-8");
                                                    json.writeValue(
                                                            res.getOutputStream(),
                                                            new RestErrors.ErrorBody(
                                                                    401,
                                                                    "HTTP_401",
                                                                    "Требуется вход",
                                                                    String.valueOf(req.getAttribute("correlationId")),
                                                                    Map.of()));
                                                })
                                        .accessDeniedHandler(
                                                (req, res, ex) -> {
                                                    res.setStatus(403);
                                                    res.setContentType("application/json;charset=UTF-8");
                                                    json.writeValue(
                                                            res.getOutputStream(),
                                                            new RestErrors.ErrorBody(
                                                                    403,
                                                                    "HTTP_403",
                                                                    "Недостаточно прав",
                                                                    String.valueOf(req.getAttribute("correlationId")),
                                                                    Map.of()));
                                                }))
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    FilterRegistrationBean<JwtFilter> disableDoubleRegistration(
            JwtFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    CorsConfigurationSource cors(@Value("${app.cors.allowed-origins}") String origins) {
        var c = new CorsConfiguration();
        c.setAllowedOrigins(Arrays.asList(origins.split(",")));
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(
                List.of("Authorization", "Content-Type", "X-Correlation-ID", "X-Confirm-Delete"));
        c.setExposedHeaders(List.of("X-Correlation-ID", "Content-Disposition"));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", c);
        return source;
    }

    @Bean
    OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info().title("Программный интерфейс билетной кассы").version("3"))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        "bearer",
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")))
                .addSecurityItem(
                        new SecurityRequirement().addList("bearer"));
    }
}
