package ru.ticketpoint.repository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import ru.ticketpoint.config.ConnectionProvider;
import ru.ticketpoint.exception.BookingException;
import ru.ticketpoint.exception.EntityNotFoundException;
import ru.ticketpoint.model.Booking;
import ru.ticketpoint.model.BookingSort;
import ru.ticketpoint.model.BookingStatistics;
import ru.ticketpoint.model.BookingStatus;
import ru.ticketpoint.model.Customer;
import ru.ticketpoint.model.Event;

public final class JdbcBookingRepository implements BookingRepository {
    private static final String SELECT_BASE = """
            SELECT b.id booking_id, b.ticket_number, b.price_at_booking, b.status, b.created_at, COALESCE((to_jsonb(b)->>'version')::bigint, 0) AS booking_version,
                   c.id AS customer_record_id, c.customer_code, c.name customer_name,
                   e.id AS event_record_id, e.title, e.venue, e.starts_at, e.price,
                   COALESCE((to_jsonb(e)->>'active')::boolean, true) AS event_active
            FROM (SELECT * FROM bookings WHERE NOT COALESCE((to_jsonb(bookings)->>'archived')::boolean, false)) b
            JOIN customers c ON c.id = b.customer_id
            JOIN events e ON e.id = b.event_id
            """;
    private final ConnectionProvider connections;
    private final JdbcQueries queries;

    public JdbcBookingRepository(ConnectionProvider connections) {
        this.connections = Objects.requireNonNull(connections);
        this.queries = new JdbcQueries(connections);
    }

    @Override
    public Booking create(long customerId, long eventId, String ticketNumber) {
        return transaction(connection -> {
            String sql = """
                    INSERT INTO bookings(ticket_number, customer_id, event_id, price_at_booking)
                    SELECT ?, ?, id, price FROM events WHERE id = ? AND starts_at > LOCALTIMESTAMP
                      AND COALESCE((to_jsonb(events)->>'active')::boolean, true)
                    RETURNING id
                    """;
            long id;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, ticketNumber);
                statement.setLong(2, customerId);
                statement.setLong(3, eventId);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        throw new BookingException("Мероприятие удалено или уже началось. Обновите список и выберите другое");
                    }
                    id = result.getLong(1);
                }
            }
            return findById(connection, id)
                    .orElseThrow(() -> new EntityNotFoundException("Бронирование не найдено"));
        });
    }

    @Override
    public List<Booking> findAll() {
        return findAll(BookingSort.CREATED_AT_DESC);
    }

    @Override
    public List<Booking> findByEventTitle(String title) {
        return query(connection -> connection.prepareStatement(SELECT_BASE + " WHERE strpos(lower(e.title), lower(?)) > 0"),
                statement -> statement.setString(1, title));
    }

    @Override
    public List<Booking> findByStatus(BookingStatus status) {
        return query(connection -> connection.prepareStatement(SELECT_BASE + " WHERE b.status = ?::booking_status"),
                statement -> statement.setString(1, status.name()));
    }

    @Override
    public List<Booking> findByCustomerId(long customerId) {
        return query(connection -> connection.prepareStatement(SELECT_BASE + " WHERE b.customer_id = ?"),
                statement -> statement.setLong(1, customerId));
    }

    @Override
    public List<Booking> findAll(BookingSort sort) {
        Objects.requireNonNull(sort);
        JdbcQueries.StatementFactory factory = switch (sort) {
            case CREATED_AT_DESC -> connection -> connection.prepareStatement(
                    SELECT_BASE + " ORDER BY b.created_at DESC, b.id");
            case EVENT_DATE_ASC -> connection -> connection.prepareStatement(
                    SELECT_BASE + " ORDER BY e.starts_at, b.id");
        };
        return query(factory, JdbcQueries.Parameters.NONE);
    }

    @Override
    public Optional<Booking> findById(long id) {
        return queries.one(connection -> connection.prepareStatement(SELECT_BASE + " WHERE b.id = ?"), statement -> statement.setLong(1, id),
                JdbcBookingRepository::map, "Не удалось прочитать бронирование");
    }

    @Override
    public Optional<Booking> findByTicketNumber(String number) {
        return queries.one(connection -> connection.prepareStatement(SELECT_BASE + " WHERE b.ticket_number = ?"), statement -> statement.setString(1, number),
                JdbcBookingRepository::map, "Не удалось прочитать бронирование");
    }

    public void updateStatus(long id, BookingStatus expected, BookingStatus next) {
        expected.validateTransitionTo(next);
        transaction(connection -> {
            String sql = "UPDATE bookings SET status = ?::booking_status "
                    + "WHERE id = ? AND status = ?::booking_status "
                    + "AND NOT COALESCE((to_jsonb(bookings)->>'archived')::boolean, false)";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, next.name());
                statement.setLong(2, id);
                statement.setString(3, expected.name());
                if (statement.executeUpdate() == 0) {
                    if (findById(connection, id).isEmpty()) {
                        throw new EntityNotFoundException("Бронирование не найдено");
                    }
                    throw new BookingException("Бронирование уже изменено другим пользователем. "
                            + "Закройте форму, обновите список и повторите изменение");
                }
            }
            return findById(connection, id)
                    .orElseThrow(() -> new EntityNotFoundException("Бронирование не найдено"));
        });
    }

    @Override
    public Booking update(Booking expected, long customerId, long eventId, BookingStatus next) {
        expected.status().validateTransitionTo(next);
        boolean detailsChanged = customerId != expected.customer().id() || eventId != expected.event().id();
        if (detailsChanged && expected.status() != BookingStatus.NEW) {
            throw new BookingException("Клиента и мероприятие можно менять только у нового бронирования");
        }
        return transaction(connection -> {
            String sql = """
                    UPDATE bookings b
                    SET customer_id = ?, event_id = e.id, status = ?::booking_status,
                        price_at_booking = CASE WHEN b.event_id = e.id THEN b.price_at_booking ELSE e.price END
                    FROM events e, events original
                    WHERE b.id = ? AND e.id = ? AND original.id = b.event_id
                      AND b.customer_id = ? AND b.event_id = ?
                      AND b.status = ?::booking_status AND b.price_at_booking = ?
                      AND COALESCE((to_jsonb(b)->>'version')::bigint, 0) = ?
                      AND NOT COALESCE((to_jsonb(b)->>'archived')::boolean, false)
                      AND (NOT ? OR (original.starts_at > LOCALTIMESTAMP AND e.starts_at > LOCALTIMESTAMP))
                      AND (b.event_id = e.id OR COALESCE((to_jsonb(e)->>'active')::boolean, true))
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, customerId);
                statement.setString(2, next.name());
                statement.setLong(3, expected.id());
                statement.setLong(4, eventId);
                statement.setLong(5, expected.customer().id());
                statement.setLong(6, expected.event().id());
                statement.setString(7, expected.status().name());
                statement.setBigDecimal(8, expected.priceAtBooking());
                statement.setLong(9, expected.version());
                statement.setBoolean(10, detailsChanged || next == BookingStatus.CONFIRMED || next == BookingStatus.PAID);
                if (statement.executeUpdate() == 0) {
                    Booking current = findById(connection, expected.id())
                            .orElseThrow(() -> new EntityNotFoundException("Бронирование не найдено"));
                    current.requireUnchanged(expected);
                    throw new BookingException("Мероприятие удалено или уже началось. "
                            + "Обновите список и выберите будущее мероприятие");
                }
            }
            return findById(connection, expected.id())
                    .orElseThrow(() -> new EntityNotFoundException("Бронирование не найдено"));
        });
    }

    @Override
    public boolean delete(Booking expected) {
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement((JdbcSchema.hasColumn(connection, "bookings", "archived")
                     ? "UPDATE bookings SET archived=true WHERE NOT archived AND id=? AND customer_id=? "
                     : "DELETE FROM bookings WHERE id=? AND customer_id=? ")
                     + "AND event_id=? AND status=?::booking_status AND price_at_booking=? "
                     + "AND COALESCE((to_jsonb(bookings)->>'version')::bigint,0)=?")) {
            statement.setLong(1, expected.id());
            statement.setLong(2, expected.customer().id());
            statement.setLong(3, expected.event().id());
            statement.setString(4, expected.status().name());
            statement.setBigDecimal(5, expected.priceAtBooking());
            statement.setLong(6, expected.version());
            return statement.executeUpdate() > 0;
        } catch (SQLException error) {
            throw SqlExceptionTranslator.translate("Не удалось удалить бронирование", error);
        }
    }


    @Override
    public BookingStatistics statistics(BigDecimal threshold) {
        Objects.requireNonNull(threshold, "Порог цены обязателен");
        String sql = """
                SELECT COUNT(*) AS total,
                       COUNT(*) FILTER (WHERE b.status = 'NEW') AS new_bookings,
                       COUNT(*) FILTER (WHERE b.status = 'CONFIRMED') AS confirmed,
                       COUNT(*) FILTER (WHERE b.status = 'PAID') AS paid,
                       COUNT(*) FILTER (WHERE b.status = 'CANCELLED') AS cancelled,
                       COUNT(*) FILTER (WHERE b.price_at_booking >= ?) AS expensive
                FROM (SELECT * FROM bookings WHERE NOT COALESCE((to_jsonb(bookings)->>'archived')::boolean, false)) b
                """;
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBigDecimal(1, threshold);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Агрегатный запрос не вернул результат");
                }
                return new BookingStatistics(result.getLong("total"), result.getLong("new_bookings"),
                        result.getLong("confirmed"), result.getLong("paid"), result.getLong("cancelled"), result.getLong("expensive"));
            }
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate("Не удалось получить статистику", e);
        }
    }

    private List<Booking> query(JdbcQueries.StatementFactory factory, JdbcQueries.Parameters parameters) {
        return queries.list(factory, parameters, JdbcBookingRepository::map, "Не удалось прочитать бронирования");
    }

    private Optional<Booking> findById(Connection connection, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_BASE + " WHERE b.id = ?")) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        }
    }

    private Booking transaction(Transaction work) {
        try (Connection connection = connections.open()) {
            connection.setAutoCommit(false);
            try {
                Booking result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                throw e;
            }
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate("Не удалось сохранить бронирование", e);
        }
    }

    private static Booking map(ResultSet result) throws SQLException {
        Customer customer = new Customer(result.getLong("customer_record_id"), result.getString("customer_code"),
                result.getString("customer_name"));
        Event event = new Event(result.getLong("event_record_id"), result.getString("title"),
                result.getString("venue"), result.getObject("starts_at", LocalDateTime.class),
                result.getBigDecimal("price"), result.getBoolean("event_active"));
        return new Booking(result.getLong("booking_id"), result.getString("ticket_number"), customer, event,
                result.getBigDecimal("price_at_booking"), BookingStatus.valueOf(result.getString("status")),
                result.getObject("created_at", LocalDateTime.class), result.getLong("booking_version"));
    }

    @FunctionalInterface
    private interface Transaction {
        Booking run(Connection connection) throws SQLException;
    }
}
