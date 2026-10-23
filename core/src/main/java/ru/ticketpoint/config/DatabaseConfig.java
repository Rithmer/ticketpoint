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
            throw new IllegalArgumentException("В настройках не указан адрес базы данных PostgreSQL.");
        }
        if (user == null || user.isBlank()) {
            throw new IllegalArgumentException("В настройках не указано имя пользователя базы данных.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Не задан пароль базы данных. Проверьте файл настроек подключения.");
        }
        Objects.requireNonNull(zone, "Часовой пояс должен быть указан");
        if (!ZoneId.getAvailableZoneIds().contains(zone.getId())) {
            throw new IllegalArgumentException("Укажите корректный часовой пояс, например Europe/Moscow или UTC.");
        }
        validateTimeout("DB_CONNECT_TIMEOUT_SECONDS", connectTimeoutSeconds);
        validateTimeout("DB_STATEMENT_TIMEOUT_SECONDS", statementTimeoutSeconds);
        validateTimeout("DB_SOCKET_TIMEOUT_SECONDS", socketTimeoutSeconds);
        if (socketTimeoutSeconds <= statementTimeoutSeconds) {
            throw new IllegalArgumentException("Время ожидания соединения должно быть больше времени ожидания запроса.");
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
            throw new IllegalArgumentException("Указан неизвестный часовой пояс. Проверьте файл настроек.", e);
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
            throw new IllegalArgumentException("В настройках времени ожидания укажите целое число секунд от 1 до 86400.");
        }
    }

    private static void validateTimeout(String name, int value) {
        if (value < 1 || value > 86400) {
            throw new IllegalArgumentException("Время ожидания должно составлять от 1 до 86400 секунд.");
        }
    }

    @Override
    public @NotNull String toString() {
        return "DatabaseConfig[connection=<hidden>, zone=" + zone + ", connectTimeoutSeconds="
                + connectTimeoutSeconds + ", statementTimeoutSeconds=" + statementTimeoutSeconds
                + ", socketTimeoutSeconds=" + socketTimeoutSeconds + "]";
    }
}
