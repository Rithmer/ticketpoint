package ru.ticketpoint.repository;

import ru.ticketpoint.config.ConnectionProvider;
import ru.ticketpoint.model.CreateEventCommand;
import ru.ticketpoint.model.Event;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class JdbcEventRepository implements EventRepository {
    private final ConnectionProvider connections;

    public JdbcEventRepository(ConnectionProvider connections) {
        this.connections = Objects.requireNonNull(connections);
    }

    @Override
    public Event create(CreateEventCommand event) {
        String sql = "INSERT INTO events(title, venue, starts_at, price) VALUES (?, ?, ?, ?) "
                + "RETURNING id, title, venue, starts_at, price";
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, event.title());
            statement.setString(2, event.venue());
            statement.setObject(3, event.startsAt());
            statement.setBigDecimal(4, event.price());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("INSERT мероприятия не вернул созданную запись");
                }
                return map(result);
            }
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate("Не удалось создать мероприятие", e);
        }
    }

    @Override
    public List<Event> findAll() {
        return query("SELECT id, title, venue, starts_at, price FROM events ORDER BY starts_at, id", null);
    }

    @Override
    public Optional<Event> findById(long id) {
        String sql = "SELECT id, title, venue, starts_at, price FROM events WHERE id = ?";
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate("Не удалось прочитать мероприятие", e);
        }
    }

    private List<Event> query(String sql, Long id) {
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (id != null) {
                statement.setLong(1, id);
            }
            try (ResultSet result = statement.executeQuery()) {
                List<Event> events = new ArrayList<>();
                while (result.next()) {
                    events.add(map(result));
                }
                return List.copyOf(events);
            }
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate("Не удалось прочитать мероприятия", e);
        }
    }

    private static Event map(ResultSet result) throws SQLException {
        return new Event(result.getLong("id"), result.getString("title"), result.getString("venue"),
                result.getObject("starts_at", LocalDateTime.class), result.getBigDecimal("price"));
    }
}
