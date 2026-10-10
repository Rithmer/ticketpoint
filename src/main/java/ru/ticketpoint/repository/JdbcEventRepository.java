package ru.ticketpoint.repository;

import ru.ticketpoint.config.ConnectionProvider;
import ru.ticketpoint.model.CreateEventCommand;
import ru.ticketpoint.model.Event;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public final class JdbcEventRepository implements EventRepository {
    private final JdbcQueries queries;

    public JdbcEventRepository(ConnectionProvider connections) {
        this.queries = new JdbcQueries(connections);
    }

    @Override
    public Event create(CreateEventCommand event) {
        String sql = "INSERT INTO events(title, venue, starts_at, price) VALUES (?, ?, ?, ?) "
                + "RETURNING id, title, venue, starts_at, price";
        return queries.one(connection -> connection.prepareStatement(sql), statement -> {
            statement.setString(1, event.title());
            statement.setString(2, event.venue());
            statement.setObject(3, event.startsAt());
            statement.setBigDecimal(4, event.price());
        }, JdbcEventRepository::map, "Не удалось создать мероприятие")
                .orElseThrow(() -> new IllegalStateException("INSERT мероприятия не вернул созданную запись"));
    }

    @Override
    public List<Event> findAll() {
        return queries.list(connection -> connection.prepareStatement("SELECT id, title, venue, starts_at, price FROM events ORDER BY starts_at, id"),
                JdbcQueries.Parameters.NONE, JdbcEventRepository::map, "Не удалось прочитать мероприятия");
    }

    @Override
    public Optional<Event> findById(long id) {
        String sql = "SELECT id, title, venue, starts_at, price FROM events WHERE id = ?";
        return queries.one(connection -> connection.prepareStatement(sql), statement -> statement.setLong(1, id),
                JdbcEventRepository::map, "Не удалось прочитать мероприятие");
    }

    private static Event map(ResultSet result) throws SQLException {
        return new Event(result.getLong("id"), result.getString("title"), result.getString("venue"),
                result.getObject("starts_at", LocalDateTime.class), result.getBigDecimal("price"));
    }
}
