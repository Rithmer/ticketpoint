package ru.ticketpoint.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Properties;

import org.postgresql.Driver;

public final class JdbcConnectionProvider implements ConnectionProvider {
    private final DatabaseConfig config;

    public JdbcConnectionProvider(DatabaseConfig config) {
        this.config = Objects.requireNonNull(config);
    }

    @Override
    public Connection open() throws SQLException {
        Properties properties = Driver.parseURL(config.url(), new Properties());
        if (properties == null) {
            throw new SQLException("Некорректный JDBC URL PostgreSQL", "08001");
        }
        properties.setProperty("user", config.user());
        properties.setProperty("password", config.password());
        properties.setProperty("connectTimeout", Integer.toString(config.connectTimeoutSeconds()));
        properties.setProperty("socketTimeout", Integer.toString(config.socketTimeoutSeconds()));
        Connection connection = DriverManager.getConnection("jdbc:postgresql:///", properties);
        try {
            try (var ps = connection.prepareStatement(
                    "SELECT set_config('TimeZone', ?, false), set_config('statement_timeout', ?, false)")) {
                ps.setQueryTimeout(config.statementTimeoutSeconds());
                ps.setString(1, config.zone().getId());
                ps.setString(2, config.statementTimeoutSeconds() + "s");
                try (var rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("Не удалось настроить сессию PostgreSQL");
                    }
                }
            }
            return connection;
        } catch (SQLException | RuntimeException e) {
            try {
                connection.close();
            } catch (SQLException closeError) {
                e.addSuppressed(closeError);
            }
            throw e;
        }
    }
}
