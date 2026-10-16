package ru.ticketpoint.backend;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import ru.ticketpoint.auth.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

@Configuration
public class ApiSecurity implements WebMvcConfigurer, HandlerInterceptor {
    private final AuthService auth;
    public ApiSecurity(AuthService auth) { this.auth = auth; }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/**");
    }
    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        boolean read = Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod());
        if (!read) {
            var session = request.getSession(false);
            if (session == null) throw new AuthException("Сеанс завершён. Войдите заново");
            String expected = (String) session.getAttribute(AuthController.CSRF);
            String actual = request.getHeader("X-CSRF-Token");
            if (expected == null || actual == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8))) {
                throw new AuthException("Сессия формы устарела. Обновите страницу и повторите действие", true);
            }
        }
        String path = request.getRequestURI();
        if (path.equals("/api/auth/session") || path.equals("/api/auth/login") || path.equals("/api/auth/register")) return true;
        User user = AuthController.user(request);
        auth.current(user);
        if (path.equals("/api/auth/password") || path.equals("/api/auth/logout")) return true;
        if (path.startsWith("/api/auth/users")) auth.requireAdmin(user);
        else if (read || path.equals("/api/bookings") || path.startsWith("/api/bookings/")) auth.requireRead(user);
        else auth.requireWrite(user);
        return true;
    }
}
