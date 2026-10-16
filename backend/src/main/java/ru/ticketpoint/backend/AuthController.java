package ru.ticketpoint.backend;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.ticketpoint.auth.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public final class AuthController {
    static final String USER = "ticketpoint.user";
    static final String CSRF = "ticketpoint.csrf";
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }
    static User user(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (User) session.getAttribute(USER);
    }
    static String csrf(HttpSession session) {
        String token = (String) session.getAttribute(CSRF);
        if (token == null) { token = UUID.randomUUID().toString(); session.setAttribute(CSRF, token); }
        return token;
    }
    @GetMapping("/session") public Session session(HttpServletRequest request) {
        User user = user(request);
        if (user != null) {
            try { user = auth.current(user); }
            catch (AuthException expired) { request.getSession().invalidate(); user = null; }
        }
        return new Session(user, csrf(request.getSession()));
    }
    @PostMapping("/login") public Session login(@RequestBody Login credentials, HttpServletRequest request) {
        User user = auth.authenticate(credentials.login(), credentials.password());
        request.changeSessionId();
        HttpSession session = request.getSession(); session.setAttribute(USER, user);
        session.setAttribute(CSRF, UUID.randomUUID().toString());
        return new Session(user, csrf(session));
    }
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) { request.getSession().invalidate(); }
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public Session register(@RequestBody Registration registration, HttpServletRequest request) {
        User user = auth.register(registration.login(), registration.name(), registration.password(), registration.confirmation());
        request.changeSessionId();
        HttpSession session = request.getSession(); session.setAttribute(USER, user);
        session.setAttribute(CSRF, UUID.randomUUID().toString());
        return new Session(user, csrf(session));
    }
    @PostMapping("/password") public Session password(@RequestBody Password credentials, HttpServletRequest request) {
        User updated = auth.changePassword(user(request), credentials.currentPassword(), credentials.newPassword());
        request.changeSessionId(); request.getSession().setAttribute(USER, updated);
        request.getSession().setAttribute(CSRF, UUID.randomUUID().toString());
        return new Session(updated, csrf(request.getSession()));
    }
    @GetMapping("/users") public List<User> users(HttpServletRequest request) { return auth.list(user(request)); }
    @PostMapping("/users") @ResponseStatus(HttpStatus.CREATED)
    public User create(@RequestBody NewUser user, HttpServletRequest request) {
        return auth.create(user(request), user.login(), user.name(), user.password(), user.role());
    }
    @PutMapping("/users/{id}") public User update(@PathVariable long id, @RequestBody EditUser edit, HttpServletRequest request) {
        if (edit.expected() == null || edit.expected().id() != id) throw new IllegalArgumentException("Пользователь не совпадает");
        User updated = auth.update(user(request), edit.expected(), edit.name(), edit.role());
        if (user(request).id() == id) request.getSession().setAttribute(USER, updated);
        return updated;
    }
    @PatchMapping("/users/{id}/access") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void access(@PathVariable long id, @RequestBody Access access, HttpServletRequest request) {
        auth.setEnabled(user(request), id, access.enabled());
    }
    public record Session(User user, String csrfToken) { }
    public record Login(String login, String password) {
        @Override public String toString() { return "Login[credentials=<hidden>]"; }
    }
    public record Password(String currentPassword, String newPassword) {
        @Override public String toString() { return "Password[credentials=<hidden>]"; }
    }
    public record NewUser(String login, String name, String password, Role role) {
        @Override public String toString() { return "NewUser[credentials=<hidden>]"; }
    }
    public record Access(boolean enabled) { }
    public record Registration(String login, String name, String password, String confirmation) {
        @Override public String toString() { return "Registration[credentials=<hidden>]"; }
    }
    public record EditUser(User expected, String name, Role role) { }
}
