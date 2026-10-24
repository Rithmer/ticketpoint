package ru.ticketpoint.backend.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.util.UUID;

import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest req, @NonNull HttpServletResponse res, @NonNull FilterChain chain)
            throws ServletException, IOException {
        String id = req.getHeader("X-Correlation-ID");
        if (id == null || !id.matches("[a-zA-Z0-9._-]{1,100}")) id = UUID.randomUUID().toString();
        req.setAttribute("correlationId", id);
        res.setHeader("X-Correlation-ID", id);
        res.setHeader("Cache-Control", "no-store");
        MDC.put("correlationId", id);
        try {
            chain.doFilter(req, res);
        } finally {
            MDC.remove("correlationId");
        }
    }
}
