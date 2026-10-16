package ru.ticketpoint.repository;

import ru.ticketpoint.config.ConnectionProvider;

import java.util.List;

public final class JdbcDatabaseMetadataRepository implements DatabaseMetadataRepository {
    private final JdbcQueries queries;

    public JdbcDatabaseMetadataRepository(ConnectionProvider connections) {
        this.queries = new JdbcQueries(connections);
    }

    @Override
    public List<String> findTableNames() {
        String sql = "SELECT tablename FROM pg_tables WHERE schemaname = current_schema() ORDER BY tablename";
        return queries.list(connection -> connection.prepareStatement(sql), JdbcQueries.Parameters.NONE, result -> result.getString(1),
                "Не удалось получить таблицы");
    }
}
