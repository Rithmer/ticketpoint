package ru.ticketpoint.repository;

import ru.ticketpoint.config.ConnectionProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class JdbcDatabaseMetadataRepository implements DatabaseMetadataRepository {
    private final ConnectionProvider connections;

    public JdbcDatabaseMetadataRepository(ConnectionProvider connections) {
        this.connections = Objects.requireNonNull(connections);
    }

    @Override
    public List<String> findTableNames() {
        String sql = "SELECT tablename FROM pg_tables WHERE schemaname = current_schema() ORDER BY tablename";
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            List<String> names = new ArrayList<>();
            while (result.next()) {
                names.add(result.getString(1));
            }
            return List.copyOf(names);
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate("Не удалось получить таблицы", e);
        }
    }
}
