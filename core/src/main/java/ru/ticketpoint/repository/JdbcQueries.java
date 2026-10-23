package ru.ticketpoint.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import ru.ticketpoint.config.ConnectionProvider;

final class JdbcQueries {
    private final ConnectionProvider connections;

    JdbcQueries(ConnectionProvider connections) {
        this.connections = Objects.requireNonNull(connections);
    }

    <T> List<T> list(StatementFactory factory, Parameters parameters, RowMapper<T> mapper, String operation) {
        try (var connection = connections.open(); var statement = factory.prepare(connection)) {
            parameters.bind(statement);
            try (var result = statement.executeQuery()) {
                List<T> rows = new ArrayList<>();
                while (result.next()) rows.add(mapper.map(result));
                return List.copyOf(rows);
            }
        } catch (SQLException error) {
            throw SqlExceptionTranslator.translate(operation, error);
        }
    }

    <T> Optional<T> one(StatementFactory factory, Parameters parameters, RowMapper<T> mapper, String operation) {
        return list(factory, parameters, mapper, operation).stream().findFirst();
    }

    @FunctionalInterface
    interface StatementFactory {
        PreparedStatement prepare(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    interface Parameters {
        Parameters NONE = statement -> {
        };

        void bind(PreparedStatement statement) throws SQLException;
    }

    @FunctionalInterface
    interface RowMapper<T> {
        T map(ResultSet result) throws SQLException;
    }
}
