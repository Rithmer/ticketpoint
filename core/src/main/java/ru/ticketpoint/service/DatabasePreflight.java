package ru.ticketpoint.service;

import java.sql.SQLException;
import java.util.Objects;

import ru.ticketpoint.config.ConnectionProvider;

public final class DatabasePreflight {
    private final ConnectionProvider connections;

    public DatabasePreflight(ConnectionProvider connections) {
        this.connections = Objects.requireNonNull(connections);
    }

    public void verify() throws SQLException {
        try (var connection = connections.open();
             var statement = connection.prepareStatement(
                     "SELECT to_regclass('customers'), to_regclass('events'), to_regclass('bookings')");
             var result = statement.executeQuery()) {
            if (!result.next()
                    || result.getString(1) == null
                    || result.getString(2) == null
                    || result.getString(3) == null) {
                throw new SQLException("В базе данных отсутствуют необходимые таблицы. Обратитесь к администратору.", "42P01");
            }
        }
    }
}
