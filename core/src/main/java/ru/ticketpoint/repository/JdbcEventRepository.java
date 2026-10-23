package ru.ticketpoint.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import ru.ticketpoint.config.ConnectionProvider;
import ru.ticketpoint.exception.EntityNotFoundException;
import ru.ticketpoint.model.CreateEventCommand;
import ru.ticketpoint.model.Event;

public final class JdbcEventRepository implements EventRepository {
    private final JdbcQueries queries;

    public JdbcEventRepository(ConnectionProvider connections) {
        this.queries = new JdbcQueries(connections);
    }

    @Override
    public Event create(CreateEventCommand event) {
        String sql = "INSERT INTO events(title, venue, starts_at, price) VALUES (?, ?, ?, ?) "
                + "RETURNING id, title, venue, starts_at, price, COALESCE((to_jsonb(events)->>'active')::boolean, true) AS active";
        return queries.one(connection -> connection.prepareStatement(sql), statement -> {
                    statement.setString(1, event.title());
                    statement.setString(2, event.venue());
                    statement.setObject(3, event.startsAt());
                    statement.setBigDecimal(4, event.price());
                }, JdbcEventRepository::map, "Не удалось создать мероприятие")
                .orElseThrow(() -> new IllegalStateException("INSERT мероприятия не вернул созданную запись"));
    }

    @Override
    public Event update(long id, CreateEventCommand command) {
        return queries.one(c -> c.prepareStatement("UPDATE events SET title=?,venue=?,starts_at=?,price=? WHERE id=? RETURNING *, COALESCE((to_jsonb(events)->>'active')::boolean, true) AS active"), statement -> {
                    statement.setString(1, command.title());
                    statement.setString(2, command.venue());
                    statement.setObject(3, command.startsAt());
                    statement.setBigDecimal(4, command.price());
                    statement.setLong(5, id);
                }, JdbcEventRepository::map, "Не удалось изменить запись")
                .orElseThrow(() -> new EntityNotFoundException("Запись не найдена"));
    }

    @Override
    public boolean delete(long id) {
        return queries.one(c -> c.prepareStatement(JdbcSchema.hasColumn(c, "events", "active")
                        ? "UPDATE events SET active=false WHERE id=? RETURNING *, COALESCE((to_jsonb(events)->>'active')::boolean, true) AS active" : "DELETE FROM events WHERE id=? RETURNING *, COALESCE((to_jsonb(events)->>'active')::boolean, true) AS active"),
                statement -> statement.setLong(1, id), JdbcEventRepository::map, "Не удалось удалить запись").isPresent();
    }

    @Override
    public List<Event> findAll() {
        return queries.list(connection -> connection.prepareStatement("SELECT id, title, venue, starts_at, price, COALESCE((to_jsonb(events)->>'active')::boolean, true) AS active FROM events ORDER BY starts_at, id"),
                JdbcQueries.Parameters.NONE, JdbcEventRepository::map, "Не удалось прочитать мероприятия");
    }

    @Override
    public Optional<Event> findById(long id) {
        String sql = "SELECT id, title, venue, starts_at, price, COALESCE((to_jsonb(events)->>'active')::boolean, true) AS active FROM events WHERE id = ?";
        return queries.one(connection -> connection.prepareStatement(sql), statement -> statement.setLong(1, id),
                JdbcEventRepository::map, "Не удалось прочитать мероприятие");
    }

    private static Event map(ResultSet result) throws SQLException {
        return new Event(result.getLong("id"), result.getString("title"), result.getString("venue"),
                result.getObject("starts_at", LocalDateTime.class), result.getBigDecimal("price"), result.getBoolean("active"));
    }
}
