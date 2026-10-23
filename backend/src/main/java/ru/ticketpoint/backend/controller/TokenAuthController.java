package ru.ticketpoint.backend.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;
import ru.ticketpoint.backend.dto.*;
import ru.ticketpoint.backend.security.CurrentUser;
import ru.ticketpoint.backend.service.TokenAuthService;

@RestController
@RequestMapping("/api/auth")
public class TokenAuthController {
    private final TokenAuthService auth;
    private final CurrentUser current;

    @Value("${COOKIE_SECURE:false}")
    private boolean secure;

    public TokenAuthController(TokenAuthService auth, CurrentUser current) {
        this.auth = auth;
        this.current = current;
    }

    @PostMapping("/login")
    public Views.Tokens login(
            @Valid @RequestBody Requests.Login r, HttpServletResponse res) {
        var tokens = auth.login(r);
        docsCookie(res, tokens);
        return tokens;
    }

    @PostMapping("/refresh")
    public Views.Tokens refresh(
            @Valid @RequestBody Requests.Refresh r, HttpServletResponse res) {
        var tokens = auth.refresh(r.refreshToken());
        docsCookie(res, tokens);
        return tokens;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Views.Tokens register(@Valid @RequestBody Requests.Registration request) {
        return auth.register(request);
    }

    @GetMapping("/me")
    public Views.UserView me() {
        return Views.user(current.get());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @Valid @RequestBody Requests.Refresh r, HttpServletResponse res) {
        auth.logout(r.refreshToken());
        res.addHeader(
                "Set-Cookie",
                ResponseCookie.from("ticketpoint_docs", "")
                        .httpOnly(true)
                        .secure(secure)
                        .sameSite("Strict")
                        .path("/")
                        .maxAge(0)
                        .build()
                        .toString());
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void password(@Valid @RequestBody Requests.Password r) {
        auth.password(r);
    }

    private void docsCookie(HttpServletResponse res, Views.Tokens tokens) {
        if (!"ADMIN".equals(tokens.user().role())) return;
        res.addHeader(
                "Set-Cookie",
                ResponseCookie.from("ticketpoint_docs", tokens.accessToken())
                        .httpOnly(true)
                        .secure(secure)
                        .sameSite("Strict")
                        .path("/")
                        .maxAge(Duration.between(Instant.now(), tokens.accessExpiresAt()))
                        .build()
                        .toString());
    }
}
