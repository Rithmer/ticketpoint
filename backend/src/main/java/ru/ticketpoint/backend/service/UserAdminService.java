package ru.ticketpoint.backend.service;

import java.time.Instant;
import java.util.*;

import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ticketpoint.auth.PasswordHasher;
import ru.ticketpoint.backend.domain.*;
import ru.ticketpoint.backend.dto.*;
import ru.ticketpoint.backend.error.ApiException;
import ru.ticketpoint.backend.repository.*;
import ru.ticketpoint.backend.security.CurrentUser;

@Service
public class UserAdminService {
    private final UserAccountRepository users;
    private final CustomerRepository customers;
    private final RefreshSessionRepository sessions;
    private final CurrentUser current;
    private final AuditService audit;

    public UserAdminService(
            UserAccountRepository users,
            RefreshSessionRepository sessions,
            CurrentUser current,
            AuditService audit,
            CustomerRepository customers) {
        this.users = users;
        this.sessions = sessions;
        this.current = current;
        this.audit = audit;
        this.customers = customers;
    }

    @Transactional(readOnly = true)
    public Page<Views.UserView> list(Pageable page) {
        return users.findAll(page).map(Views::user);
    }

    @Transactional
    public Views.UserView create(Requests.NewUser req) {
        var u = new UserAccount();
        apply(u, req.user());
        u.setPasswordHash(new PasswordHasher().hash(req.temporaryPassword()));
        users.saveAndFlush(u);
        audit.record("USER_CREATED", "USER", u.getPublicId(), "Создана учётная запись " + u.getLogin());
        return Views.user(u);
    }

    @Transactional
    public Views.UserView update(UUID id, Requests.UserInput req) {
        var u = users.findByPublicId(id).orElseThrow(ApiException::missing);
        ApiException.version(req.version(), u.getVersion());
        if (id.equals(current.get().getPublicId()) && (!req.enabled() || !"ADMIN".equals(req.role())))
            throw ApiException.bad("Нельзя отключить или понизить собственную учётную запись");
        if (u.getEnabled() && u.getRole().equals("ADMIN")
                && (!req.enabled() || !req.role().equals("ADMIN"))
                && users.lockEnabledAdmins().size() <= 1)
            throw ApiException.bad("Нельзя отключить последнего администратора");
        apply(u, req);
        users.saveAndFlush(u);
        sessions.revokeAll(u.getId(), Instant.now());
        audit.record("USER_UPDATED", "USER", id, "Изменены имя, логин или доступ пользователя");
        return Views.user(u);
    }

    @Transactional
    public void reset(UUID id, String password) {
        var u = users.findByPublicId(id).orElseThrow(ApiException::missing);
        u.setPasswordHash(new PasswordHasher().hash(password));
        u.setPasswordChangeRequired(true);
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        users.saveAndFlush(u);
        sessions.revokeAll(u.getId(), Instant.now());
        audit.record("PASSWORD_RESET", "USER", id, "Выдан временный пароль");
    }

    private void apply(UserAccount u, Requests.UserInput req) {
        String login = req.login().toLowerCase(Locale.ROOT);
        var other = users.findByLoginIgnoreCase(login).orElse(null);
        if (other != null && !other.getPublicId().equals(u.getPublicId()))
            throw new ApiException(409, "Логин уже используется");
        u.setLogin(login);
        u.setName(req.name().trim());
        u.setRole(req.role());
        u.setEnabled(req.enabled());
        if (u.getRole().equals("USER") && u.getCustomerId() == null) {
            var c = new Customer();
            c.setName(u.getName());
            c.setCode("CUS-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT));
            customers.saveAndFlush(c);
            u.setCustomerId(c.getId());
        }
    }
}
