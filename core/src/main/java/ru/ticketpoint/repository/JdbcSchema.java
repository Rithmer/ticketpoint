package ru.ticketpoint.repository;

import java.sql.Connection;
import java.sql.SQLException;

final class JdbcSchema {
    private JdbcSchema() {
    }

    static boolean hasColumn(Connection connection, String table, String column) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT EXISTS (SELECT 1 FROM pg_attribute
                  WHERE attrelid=to_regclass(?) AND attname=? AND NOT attisdropped)
                """)) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getBoolean(1);
            }
        }
    }
}
