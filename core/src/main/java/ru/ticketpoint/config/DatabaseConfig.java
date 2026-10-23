package ru.ticketpoint.config;

import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;

public record DatabaseConfig(String url, String user, String password, ZoneId zone,
                             int connectTimeoutSeconds, int statementTimeoutSeconds,
                             int socketTimeoutSeconds) {
    public DatabaseConfig {
        if (url == null || url.isBlank() || !url.startsWith("jdbc:postgresql:")) {
            throw new IllegalArgumentException("DB_URL должен быть непустым JDBC URL PostgreSQL");
        }
        if (user == null || user.isBlank()) {
            throw new IllegalArgumentException("DB_USER не должен быть пустым");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Не задан пароль базы данных. Заполните DB_PASSWORD в .env "
                    + "или POSTGRES_PASSWORD, если DB_PASSWORD ссылается на него");
        }
        Objects.requireNonNull(zone, "APP_ZONE обязателен");
        if (!ZoneId.getAvailableZoneIds().contains(zone.getId())) {
            throw new IllegalArgumentException("APP_ZONE должен быть именем зоны, например Europe/Moscow или UTC");
        }
        validateTimeout("DB_CONNECT_TIMEOUT_SECONDS", connectTimeoutSeconds);
        validateTimeout("DB_STATEMENT_TIMEOUT_SECONDS", statementTimeoutSeconds);
        validateTimeout("DB_SOCKET_TIMEOUT_SECONDS", socketTimeoutSeconds);
        if (socketTimeoutSeconds <= statementTimeoutSeconds) {
            throw new IllegalArgumentException("DB_SOCKET_TIMEOUT_SECONDS должен быть больше DB_STATEMENT_TIMEOUT_SECONDS");
        }
    }

    public static DatabaseConfig fromEnvironment() {
        return fromEnvironment(EnvironmentFile.read(Path.of(".env"), System.getenv()));
    }

    public static DatabaseConfig fromEnvironment(Map<String, String> environment) {
        ZoneId zone;
        try {
            zone = ZoneId.of(environment.getOrDefault("APP_ZONE", "Europe/Moscow"));
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Некорректный APP_ZONE: укажите имя зоны, например Europe/Moscow", e);
        }
        return new DatabaseConfig(
                environment.getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/ticketpoint"),
                environment.getOrDefault("DB_USER", "ticketpoint"),
                environment.get("DB_PASSWORD"), zone,
                timeout(environment, "DB_CONNECT_TIMEOUT_SECONDS", 5),
                timeout(environment, "DB_STATEMENT_TIMEOUT_SECONDS", 15),
                timeout(environment, "DB_SOCKET_TIMEOUT_SECONDS", 30));
    }

    private static int timeout(Map<String, String> environment, String name, int fallback) {
        try {
            return Integer.parseInt(environment.getOrDefault(name, Integer.toString(fallback)));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " должен быть целым числом секунд от 1 до 86400");
        }
    }

    private static void validateTimeout(String name, int value) {
        if (value < 1 || value > 86400) {
            throw new IllegalArgumentException(name + " должен быть от 1 до 86400 секунд");
        }
    }

    @Override
    public @NotNull String toString() {
        return "DatabaseConfig[connection=<hidden>, zone=" + zone + ", connectTimeoutSeconds="
                + connectTimeoutSeconds + ", statementTimeoutSeconds=" + statementTimeoutSeconds
                + ", socketTimeoutSeconds=" + socketTimeoutSeconds + "]";
    }
}
