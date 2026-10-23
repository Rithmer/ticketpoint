package ru.ticketpoint.auth;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import ru.ticketpoint.model.Validation;
import ru.ticketpoint.repository.JdbcUserRepository;

public final class AuthService {
    private final JdbcUserRepository users;
    private final PasswordHasher passwords = new PasswordHasher();
    private final Clock clock;
    private final String dummyHash = passwords.hash("dummy-password-for-timing");

    public AuthService(JdbcUserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    public void initialize(AuthConfig config) {
        users.initialize(() -> {
            if (config.password() == null || config.password().isBlank()) {
                throw new IllegalArgumentException("Первый администратор ещё не создан. Задайте BOOTSTRAP_ADMIN_PASSWORD в .env (8–128 символов)");
            }
            return new JdbcUserRepository.InitialAdmin(login(config.login()),
                    Validation.text(config.name(), "Имя администратора", 120), passwords.hash(config.password()));
        });
    }

    public static String login(String value) {
        String login = Validation.text(value, "Логин", 40).toLowerCase(Locale.ROOT);
        if (!login.matches("[a-z0-9][a-z0-9_.-]{2,39}")) {
            throw new IllegalArgumentException("Логин: 3–40 символов, латинские буквы, цифры, точка, дефис или подчёркивание");
        }
        return login;
    }

    public User authenticate(String login, String password) {
        String normalized = login(login);
        if (password == null || password.isEmpty() || password.length() > 128) {
            throw new AuthException("Введите логин и пароль");
        }
        return users.authenticate(normalized, hash -> passwords.matches(password, hash), clock.instant(), dummyHash)
                .orElseThrow(() -> new AuthException("Неверный логин или пароль, либо вход временно недоступен. После 5 ошибок подождите 5 минут"));
    }

    public User current(User session) {
        if (session == null) throw new AuthException("Войдите в приложение");
        User current = users.find(session.id()).orElseThrow(() -> new AuthException("Сеанс завершён. Войдите заново"));
        if (!current.enabled() || current.version() != session.version()) {
            throw new AuthException("Сеанс завершён. Войдите заново");
        }
        return current;
    }

    public void requireRead(User session) {
        if (current(session).passwordChangeRequired()) throw new AuthException("Смените временный пароль", true);
    }

    public void requireWrite(User session) {
        requireRead(session);
        if (!current(session).role().canWrite())
            throw new AuthException("Недостаточно прав для изменения данных", true);
    }

    public void requireAdmin(User session) {
        requireRead(session);
        if (current(session).role() != Role.ADMIN)
            throw new AuthException("Доступ разрешён только администратору", true);
    }

    public List<User> list(User session) {
        requireAdmin(session);
        return users.findAll();
    }

    public User register(String login, String name, String password, String confirmation) {
        String normalized = login(login);
        String displayName = Validation.text(name, "Имя", 120);
        PasswordHasher.validate(password);
        if (!Objects.equals(password, confirmation))
            throw new IllegalArgumentException("Подтверждение пароля не совпадает");
        return users.register(normalized, displayName, passwords.hash(password));
    }

    public User create(User session, String login, String name, String password, Role role) {
        requireAdmin(session);
        return users.create(login(login), Validation.text(name, "Имя пользователя", 120), passwords.hash(password), selectedRole(role));
    }

    private static Role selectedRole(Role role) {
        if (role == null) throw new IllegalArgumentException("Выберите роль пользователя");
        return role;
    }

    public void setEnabled(User session, long id, boolean enabled) {
        requireAdmin(session);
        User target = users.find(Validation.id(id)).orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
        if (target.role() == Role.ADMIN && !enabled)
            throw new IllegalArgumentException("Учётную запись администратора нельзя отключить");
        users.setEnabled(id, enabled);
    }

    public User update(User session, User expected, String name, Role role) {
        requireAdmin(session);
        User target = users.find(Validation.id(expected.id())).orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
        if (target.version() != expected.version())
            throw new IllegalArgumentException("Учётная запись уже изменена. Обновите список");
        selectedRole(role);
        if (target.role() == Role.ADMIN && role != Role.ADMIN)
            throw new IllegalArgumentException("Нельзя понизить роль администратора");
        return users.update(target, Validation.text(name, "Имя пользователя", 120), role);
    }

    public User changePassword(User session, String oldPassword, String newPassword) {
        User current = current(session);
        PasswordHasher.validate(newPassword);
        if (Objects.equals(oldPassword, newPassword))
            throw new IllegalArgumentException("Новый пароль должен отличаться от текущего");
        User verified = authenticate(current.login(), oldPassword);
        if (verified.version() != current.version()) throw new AuthException("Сеанс завершён. Войдите заново");
        return users.changePassword(current, passwords.hash(newPassword));
    }
}
