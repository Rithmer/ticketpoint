package ru.ticketpoint.backend.service;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ticketpoint.auth.AuthService;
import ru.ticketpoint.auth.PasswordHasher;
import ru.ticketpoint.backend.domain.*;
import ru.ticketpoint.backend.dto.*;
import ru.ticketpoint.backend.error.ApiException;
import ru.ticketpoint.backend.repository.*;
import ru.ticketpoint.backend.security.*;
import ru.ticketpoint.model.Validation;

@Service
public class TokenAuthService {
    private final UserAccountRepository users;
    private final RefreshSessionRepository sessions;
    private final JwtService jwt;
    private final CurrentUser current;
    private final AuditService audit;
    private final int days;
    private final PasswordHasher passwords = new PasswordHasher();
    private final String dummy = passwords.hash("Dummy-password-2026!");
    private final CustomerRepository customers;

    public TokenAuthService(
            UserAccountRepository users,
            RefreshSessionRepository sessions,
            JwtService jwt,
            CurrentUser current,
            AuditService audit,
            CustomerRepository customers,
            @Value("${app.jwt.refresh-days}") int days) {
        this.customers = customers;
        this.users = users;
        this.sessions = sessions;
        this.jwt = jwt;
        this.current = current;
        this.audit = audit;
        this.days = days;
        if (days < 1 || days > 90) throw new IllegalArgumentException("Срок восстановления сеанса должен быть от 1 до 90 дней.");
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Views.Tokens login(Requests.Login req) {
        var user = users.lockByLogin(req.login().trim().toLowerCase(Locale.ROOT)).orElse(null);
        boolean match = passwords.matches(req.password(), user == null ? dummy : user.getPasswordHash());
        Instant now = Instant.now();
        if (user == null
                || !user.getEnabled()
                || user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)
                || !match) {
            if (user != null
                    && user.getEnabled()
                    && (user.getLockedUntil() == null || !user.getLockedUntil().isAfter(now))) {
                user.setFailedAttempts(user.getLockedUntil() == null ? user.getFailedAttempts() + 1 : 1);
                user.setLockedUntil(user.getFailedAttempts() >= 5 ? now.plusSeconds(300) : null);
                users.saveAndFlush(user);
            }
            audit.record(
                    "LOGIN_FAILED", "USER", user == null ? null : user.getPublicId(), "Неудачная попытка входа");
            throw new ApiException(401, "Не удалось войти. Проверьте логин и пароль. После нескольких неудачных попыток подождите пять минут.");
        }
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        if (!user.getPasswordHash().startsWith("$2")) {
            user.setPasswordHash(passwords.hash(req.password()));
        }
        users.saveAndFlush(user);
        audit.record("LOGIN_SUCCESS", "USER", user.getPublicId(), "Успешный вход");
        return issue(user);
    }

    @Transactional
    public Views.Tokens register(Requests.Registration req) {
        if (!req.password().equals(req.confirmation()))
            throw ApiException.bad("Пароли не совпадают. Повторите пароль ещё раз.");
        String login = AuthService.login(req.login());
        if (users.findByLoginIgnoreCase(login).isPresent())
            throw new ApiException(409, "Этот логин уже занят. Выберите другой.");
        String hash = passwords.hash(req.password());
        var profile = new Customer();
        profile.setName(Validation.text(req.name(), "Имя", 120));
        profile.setCode("CUS-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT));
        customers.saveAndFlush(profile);
        var user = new UserAccount();
        user.setLogin(login);
        user.setName(profile.getName());
        user.setPasswordHash(hash);
        user.setRole("USER");
        user.setCustomerId(profile.getId());
        user.setPasswordChangeRequired(false);
        users.saveAndFlush(user);
        audit.record("USER_REGISTERED", "USER", user.getPublicId(), "Создан аккаунт покупателя");
        return issue(user);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Views.Tokens refresh(String token) {
        var s = sessions.lockByHash(hash(token)).orElseThrow(ApiException::unauthorized);
        Instant now = Instant.now();
        if (s.getRevokedAt() != null) {
            sessions.revokeAll(s.getUser().getId(), now);
            audit.record(
                    "REFRESH_REUSE", "USER", s.getUser().getPublicId(), "Повторное использование refresh-токена");
            throw ApiException.unauthorized();
        }
        if (!s.getExpiresAt().isAfter(now) || !s.getUser().getEnabled() || s.getUser().getVersion() != s.getUserVersion()) {
            s.setRevokedAt(now);
            throw ApiException.unauthorized();
        }
        s.setRevokedAt(now);
        return issue(s.getUser());
    }

    @Transactional
    public void logout(String token) {
        sessions
                .lockByHash(hash(token))
                .ifPresent(
                        s -> {
                            if (s.getUser().getId().equals(current.get().getId())) s.setRevokedAt(Instant.now());
                        });
        audit.record("LOGOUT", "USER", current.get().getPublicId(), "Выход из системы");
    }

    @Transactional
    public void password(Requests.Password req) {
        var u = current.get();
        if (!passwords.matches(req.currentPassword(), u.getPasswordHash()))
            throw ApiException.unauthorized();
        if (passwords.matches(req.newPassword(), u.getPasswordHash()))
            throw ApiException.bad("Новый пароль должен отличаться от текущего");
        u.setPasswordHash(passwords.hash(req.newPassword()));
        u.setPasswordChangeRequired(false);
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        users.saveAndFlush(u);
        sessions.revokeAll(u.getId(), Instant.now());
        audit.record("PASSWORD_CHANGED", "USER", u.getPublicId(), "Пароль изменён, сеансы отозваны");
    }

    private Views.Tokens issue(UserAccount u) {
        var s = new RefreshSession();
        s.setUser(u);
        s.setUserVersion(u.getVersion());
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        s.setTokenHash(hash(raw));
        s.setExpiresAt(Instant.now().plusSeconds(days * 86400L));
        sessions.save(s);
        Instant expires = jwt.expiry();
        return new Views.Tokens(jwt.issue(u, expires), expires, raw, s.getExpiresAt(), Views.user(u));
    }

    public static String hash(String raw) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
