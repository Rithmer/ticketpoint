package ru.ticketpoint.backend.security;

import tools.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.util.*;

import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.ticketpoint.backend.error.RestErrors;
import ru.ticketpoint.backend.repository.UserAccountRepository;

@Component
public class JwtFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final UserAccountRepository users;
    private final ObjectMapper json;

    public JwtFilter(JwtService jwt, UserAccountRepository users, ObjectMapper json) {
        this.jwt = jwt;
        this.users = users;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return Set.of("/api/auth/login", "/api/auth/refresh", "/api/auth/register").contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest req, @NonNull HttpServletResponse res, @NonNull FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header == null
                && (req.getRequestURI().startsWith("/swagger-ui")
                || req.getRequestURI().startsWith("/v3/api-docs"))
                && req.getCookies() != null)
            for (var cookie : req.getCookies())
                if (cookie.getName().equals("ticketpoint_docs")) header = "Bearer " + cookie.getValue();
        if (header != null && header.startsWith("Bearer ")) {
            try {
                var claims = jwt.parse(header.substring(7));
                var user = users.findByPublicId(UUID.fromString(claims.getSubject())).orElseThrow();
                if (!user.getEnabled()
                        || claims.get("version", Number.class) == null
                        || user.getVersion() != claims.get("version", Number.class).longValue())
                    throw new IllegalArgumentException("revoked");
                SecurityContextHolder.getContext()
                        .setAuthentication(
                                new UsernamePasswordAuthenticationToken(
                                        user.getPublicId(), null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()))));
                String path = req.getRequestURI();
                if (user.getPasswordChangeRequired()
                        && !Set.of("/api/auth/me", "/api/auth/password", "/api/auth/logout").contains(path)) {
                    error(res, 403, "Смените временный пароль", req);
                    return;
                }
            } catch (JwtException | IllegalArgumentException | NoSuchElementException e) {
                error(res, 401, "Сеанс завершён. Войдите заново", req);
                return;
            }
        }
        chain.doFilter(req, res);
    }

    private void error(HttpServletResponse res, int status, String message, HttpServletRequest req)
            throws IOException {
        SecurityContextHolder.clearContext();
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        json.writeValue(
                res.getOutputStream(),
                new RestErrors.ErrorBody(
                        status,
                        "HTTP_" + status,
                        message,
                        String.valueOf(req.getAttribute("correlationId")),
                        Map.of()));
    }
}
