package ru.ticketpoint.repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.jetbrains.annotations.NotNull;
import ru.ticketpoint.auth.*;
import ru.ticketpoint.config.ConnectionProvider;
import ru.ticketpoint.exception.BookingException;
import ru.ticketpoint.exception.DataAccessException;

public final class JdbcUserRepository {
    private final ConnectionProvider connections;
    private final JdbcQueries queries;

    public JdbcUserRepository(ConnectionProvider connections) {
        this.connections = connections;
        queries = new JdbcQueries(connections);
    }

    public void initialize(Supplier<InitialAdmin> initial) {
        try (var connection = connections.open()) {
            connection.setAutoCommit(false);
            try {
                try (var lock = connection.prepareStatement("SELECT pg_advisory_xact_lock(842701002)")) {
                    lock.execute();
                }
                try (var stream = JdbcUserRepository.class.getResourceAsStream("/db/002-auth.sql")) {
                    if (stream == null) throw new IOException("Ресурс миграции отсутствует");
                    try (var statement = connection.createStatement()) {
                        statement.execute(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                    }
                }
                try (var stream = JdbcUserRepository.class.getResourceAsStream("/db/003-customer-workflow.sql")) {
                    if (stream == null) throw new IOException("Ресурс миграции отсутствует");
                    try (var statement = connection.createStatement()) {
                        statement.execute(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                    }
                }
                try (var statement = connection.prepareStatement("SELECT count(*) FROM app_users");
                     var rows = statement.executeQuery()) {
                    rows.next();
                    if (rows.getLong(1) == 0) {
                        InitialAdmin admin = initial.get();
                        try (var insert = connection.prepareStatement(
                                "INSERT INTO app_users(login,name,password_hash,role) VALUES (?,?,?,'ADMIN')")) {
                            insert.setString(1, admin.login());
                            insert.setString(2, admin.name());
                            insert.setString(3, admin.hash());
                            insert.executeUpdate();
                        }
                    }
                }
                connection.commit();
            } catch (SQLException | IOException | RuntimeException error) {
                try {
                    connection.rollback();
                } catch (SQLException rollback) {
                    error.addSuppressed(rollback);
                }
                if (error instanceof SQLException sql)
                    throw SqlExceptionTranslator.translate("Не удалось подготовить учётные записи", sql);
                if (error instanceof IOException io)
                    throw new DataAccessException("Не удалось прочитать настройки учётных записей", io);
                throw (RuntimeException) error;
            }
        } catch (SQLException error) {
            throw SqlExceptionTranslator.translate("Не удалось подключить учётные записи", error);
        }
    }

    public Optional<User> authenticate(String login, Predicate<String> passwordMatches, Instant now, String dummyHash) {
        try (var connection = connections.open()) {
            connection.setAutoCommit(false);
            try {
                User user = null;
                try (var statement = connection.prepareStatement("SELECT * FROM app_users WHERE login=? FOR UPDATE")) {
                    statement.setString(1, login);
                    try (var row = statement.executeQuery()) {
                        if (row.next()) {
                            boolean matches = passwordMatches.test(row.getString("password_hash"));
                            Timestamp locked = row.getTimestamp("locked_until");
                            boolean blocked = locked != null && locked.toInstant().isAfter(now);
                            if (matches && row.getBoolean("enabled") && !blocked) {
                                user = map(row);
                                updateAttempts(connection, user.id(), 0, null);
                            } else if (!blocked && row.getBoolean("enabled")) {
                                int failures = locked != null ? 1 : row.getInt("failed_attempts") + 1;
                                updateAttempts(connection, row.getLong("id"), failures,
                                        failures >= 5 ? now.plusSeconds(300) : null);
                            }
                        } else {
                            passwordMatches.test(dummyHash);
                        }
                    }
                }
                connection.commit();
                return Optional.ofNullable(user);
            } catch (SQLException | RuntimeException error) {
                try {
                    connection.rollback();
                } catch (SQLException rollback) {
                    error.addSuppressed(rollback);
                }
                throw error;
            }
        } catch (SQLException error) {
            throw SqlExceptionTranslator.translate("Не удалось выполнить вход", error);
        }
    }

    private static void updateAttempts(Connection connection, long id, int failures, Instant lock) throws SQLException {
        try (var statement = connection.prepareStatement("UPDATE app_users SET failed_attempts=?,locked_until=? WHERE id=?")) {
            statement.setInt(1, failures);
            statement.setTimestamp(2, lock == null ? null : Timestamp.from(lock));
            statement.setLong(3, id);
            statement.executeUpdate();
        }
    }

    public Optional<User> find(long id) {
        return queries.one(c -> c.prepareStatement("SELECT * FROM app_users WHERE id=?"), s -> s.setLong(1, id),
                JdbcUserRepository::map, "Не удалось проверить сеанс");
    }

    public List<User> findAll() {
        return queries.list(c -> c.prepareStatement("SELECT * FROM app_users ORDER BY login"), JdbcQueries.Parameters.NONE,
                JdbcUserRepository::map, "Не удалось загрузить пользователей");
    }

    public User create(String login, String name, String hash, Role role) {
        return create(login, name, hash, role, true);
    }

    public User register(String login, String name, String hash) {
        return create(login, name, hash, Role.USER, false);
    }

    private User create(String login, String name, String hash, Role role, boolean temporaryPassword) {
        try (var c = connections.open()) {
            c.setAutoCommit(false);
            try {
                Long customerId = role == Role.USER ? createProfile(c, name) : null;
                User created;
                try (var s = c.prepareStatement("INSERT INTO app_users(login,name,password_hash,role,customer_id,password_change_required) VALUES (?,?,?,?,?,?) RETURNING *")) {
                    s.setString(1, login);
                    s.setString(2, name);
                    s.setString(3, hash);
                    s.setString(4, role.name());
                    s.setObject(5, customerId);
                    s.setBoolean(6, temporaryPassword);
                    try (var rows = s.executeQuery()) {
                        rows.next();
                        created = map(rows);
                    }
                }
                c.commit();
                return created;
            } catch (SQLException | RuntimeException error) {
                try {
                    c.rollback();
                } catch (SQLException rollback) {
                    error.addSuppressed(rollback);
                }
                if (error instanceof SQLException sql && "23505".equals(sql.getSQLState()))
                    throw new IllegalArgumentException("Логин уже занят. Выберите другой логин");
                throw error;
            }
        } catch (SQLException error) {
            throw SqlExceptionTranslator.translate("Не удалось создать пользователя", error);
        } catch (BookingException error) {
            if (error.getCause() instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                throw new IllegalArgumentException("Логин уже занят. Выберите другой логин");
            }
            throw error;
        }
    }

    private static long createProfile(Connection c, String name) throws SQLException {
        try (var s = c.prepareStatement("INSERT INTO customers(customer_code,name) VALUES (?,?) RETURNING id")) {
            s.setString(1, "CUS-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT));
            s.setString(2, name);
            try (var rows = s.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    public User update(User expected, String name, Role role) {
        try (var c = connections.open()) {
            c.setAutoCommit(false);
            try {
                Long customerId = expected.customerId();
                if (role == Role.USER && customerId == null) customerId = createProfile(c, name);
                User updated;
                try (var s = c.prepareStatement("UPDATE app_users SET name=?,role=?,customer_id=?,version=version+1 WHERE id=? AND version=? RETURNING *")) {
                    s.setString(1, name);
                    s.setString(2, role.name());
                    s.setObject(3, customerId);
                    s.setLong(4, expected.id());
                    s.setLong(5, expected.version());
                    try (var rows = s.executeQuery()) {
                        if (!rows.next())
                            throw new IllegalArgumentException("Учётная запись уже изменена. Обновите список");
                        updated = map(rows);
                    }
                }
                if (customerId != null) {
                    try (var s = c.prepareStatement("UPDATE customers SET name=? WHERE id=?")) {
                        s.setString(1, name);
                        s.setLong(2, customerId);
                        s.executeUpdate();
                    }
                }
                c.commit();
                return updated;
            } catch (SQLException | RuntimeException error) {
                try {
                    c.rollback();
                } catch (SQLException rollback) {
                    error.addSuppressed(rollback);
                }
                throw error;
            }
        } catch (SQLException error) {
            throw SqlExceptionTranslator.translate("Не удалось изменить пользователя", error);
        }
    }

    public User changePassword(User user, String hash) {
        return queries.one(c -> c.prepareStatement("UPDATE app_users SET password_hash=?,password_change_required=false,"
                        + "version=version+1 WHERE id=? AND version=? AND enabled RETURNING *"), s -> {
                    s.setString(1, hash);
                    s.setLong(2, user.id());
                    s.setLong(3, user.version());
                }, JdbcUserRepository::map, "Не удалось сменить пароль")
                .orElseThrow(() -> new AuthException("Сеанс завершён. Войдите заново"));
    }

    public void setEnabled(long id, boolean enabled) {
        queries.one(c -> c.prepareStatement("UPDATE app_users SET enabled=?,version=version+1 WHERE id=? RETURNING *"),
                s -> {
                    s.setBoolean(1, enabled);
                    s.setLong(2, id);
                }, JdbcUserRepository::map,
                "Не удалось изменить доступ").orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
    }

    private static User map(ResultSet row) throws SQLException {
        return new User(row.getLong("id"), row.getString("login"), row.getString("name"),
                Role.valueOf(row.getString("role")), row.getBoolean("enabled"),
                row.getBoolean("password_change_required"), row.getLong("version"), row.getObject("customer_id", Long.class));
    }

    public record InitialAdmin(String login, String name, String hash) {
        @Override
        public @NotNull String toString() {
            return "InitialAdmin[credentials=<hidden>]";
        }
    }
}
