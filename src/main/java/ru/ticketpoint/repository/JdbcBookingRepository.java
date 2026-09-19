package ru.ticketpoint.repository;

import ru.ticketpoint.config.ConnectionProvider;
import ru.ticketpoint.exception.BookingException;
import ru.ticketpoint.exception.EntityNotFoundException;
import ru.ticketpoint.model.Booking;
import ru.ticketpoint.model.BookingStatistics;
import ru.ticketpoint.model.BookingStatus;
import ru.ticketpoint.model.BookingSort;
import ru.ticketpoint.model.Customer;
import ru.ticketpoint.model.Event;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class JdbcBookingRepository implements BookingRepository {
    private static final String SELECT_BASE = """
            SELECT b.id booking_id, b.ticket_number, b.price_at_booking, b.status, b.created_at,
                   c.id customer_id, c.customer_code, c.name customer_name,
                   e.id event_id, e.title, e.venue, e.starts_at, e.price
            FROM bookings b
            JOIN customers c ON c.id = b.customer_id
            JOIN events e ON e.id = b.event_id
            """;
    private final ConnectionProvider connections;

    public JdbcBookingRepository(ConnectionProvider connections) {
        this.connections = Objects.requireNonNull(connections);
    }

    @Override
    public Booking create(long customerId, long eventId, String ticketNumber) {
        return transaction(connection -> {
            String sql = """
                    INSERT INTO bookings(ticket_number, customer_id, event_id, price_at_booking)
                    SELECT ?, ?, id, price FROM events WHERE id = ? AND starts_at > LOCALTIMESTAMP
                    RETURNING id
                    """;
            long id;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, ticketNumber);
                statement.setLong(2, customerId);
                statement.setLong(3, eventId);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        throw new BookingException("Мероприятие отсутствует или уже началось");
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
        return query(SELECT_BASE + " WHERE lower(e.title) LIKE '%' || lower(?) || '%'", title);
    }

    @Override
    public List<Booking> findByStatus(BookingStatus status) {
        return query(SELECT_BASE + " WHERE b.status = ?::booking_status", status.name());
    }

    @Override
    public List<Booking> findByCustomerId(long customerId) {
        return query(SELECT_BASE + " WHERE b.customer_id = ?", customerId);
    }

    @Override
    public List<Booking> findAll(BookingSort sort) {
        Objects.requireNonNull(sort);
        String order = switch (sort) {
            case CREATED_AT_DESC -> " ORDER BY b.created_at DESC, b.id";
            case EVENT_DATE_ASC -> " ORDER BY e.starts_at, b.id";
        };
        return query(SELECT_BASE + order, null);
    }

    @Override
    public Optional<Booking> findById(long id) {
        return queryOne(SELECT_BASE + " WHERE b.id = ?", id);
    }

    @Override
    public Optional<Booking> findByTicketNumber(String number) {
        return queryOne(SELECT_BASE + " WHERE b.ticket_number = ?", number);
    }

    @Override
    public Booking updateStatus(long id, BookingStatus expected, BookingStatus next) {
        expected.validateTransitionTo(next);
        return transaction(connection -> {
            String sql = "UPDATE bookings SET status = ?::booking_status "
                    + "WHERE id = ? AND status = ?::booking_status";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, next.name());
                statement.setLong(2, id);
                statement.setString(3, expected.name());
                if (statement.executeUpdate() == 0) {
                    if (findById(connection, id).isEmpty()) {
                        throw new EntityNotFoundException("Бронирование не найдено");
                    }
                    throw new BookingException("Бронирование уже изменено другим пользователем. Повторите операцию");
                }
            }
            return findById(connection, id)
                    .orElseThrow(() -> new EntityNotFoundException("Бронирование не найдено"));
        });
    }

    @Override
    public boolean delete(long id) {
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM bookings WHERE id = ?")) {
            statement.setLong(1, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate("Не удалось удалить бронирование", e);
        }
    }

    @Override
    public BookingStatistics statistics(BigDecimal threshold) {
        Objects.requireNonNull(threshold, "Порог цены обязателен");
        String sql = """
                SELECT COUNT(*) AS total,
                       COUNT(*) FILTER (WHERE b.status = 'NEW') AS new_bookings,
                       COUNT(*) FILTER (WHERE b.status = 'PAID') AS paid,
                       COUNT(*) FILTER (WHERE b.status = 'CANCELLED') AS cancelled,
                       COUNT(*) FILTER (WHERE b.price_at_booking >= ?) AS expensive
                FROM bookings b
                """;
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBigDecimal(1, threshold);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Агрегатный запрос не вернул результат");
                }
                return new BookingStatistics(result.getLong("total"), result.getLong("new_bookings"),
                        result.getLong("paid"), result.getLong("cancelled"), result.getLong("expensive"));
            }
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate("Не удалось получить статистику", e);
        }
    }

    private List<Booking> query(String sql, Object value) {
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (value != null) {
                statement.setObject(1, value);
            }
            try (ResultSet result = statement.executeQuery()) {
                List<Booking> bookings = new ArrayList<>();
                while (result.next()) {
                    bookings.add(map(result));
                }
                return List.copyOf(bookings);
            }
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate("Не удалось прочитать бронирования", e);
        }
    }

    private Optional<Booking> queryOne(String sql, Object value) {
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, value);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate("Не удалось прочитать бронирование", e);
        }
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
        Customer customer = new Customer(result.getLong("customer_id"), result.getString("customer_code"),
                result.getString("customer_name"));
        Event event = new Event(result.getLong("event_id"), result.getString("title"),
                result.getString("venue"), result.getObject("starts_at", LocalDateTime.class),
                result.getBigDecimal("price"));
        return new Booking(result.getLong("booking_id"), result.getString("ticket_number"), customer, event,
                result.getBigDecimal("price_at_booking"), BookingStatus.valueOf(result.getString("status")),
                result.getObject("created_at", LocalDateTime.class));
    }

    @FunctionalInterface
    private interface Transaction {
        Booking run(Connection connection) throws SQLException;
    }
}
