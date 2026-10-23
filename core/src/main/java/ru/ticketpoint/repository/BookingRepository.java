package ru.ticketpoint.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import ru.ticketpoint.model.Booking;
import ru.ticketpoint.model.BookingSort;
import ru.ticketpoint.model.BookingStatistics;
import ru.ticketpoint.model.BookingStatus;

public interface BookingRepository {
    Booking create(long customerId, long eventId, String ticketNumber);

    List<Booking> findAll();

    List<Booking> findByEventTitle(String title);

    List<Booking> findByStatus(BookingStatus status);

    List<Booking> findByCustomerId(long customerId);

    List<Booking> findAll(BookingSort sort);

    Optional<Booking> findById(long id);

    Optional<Booking> findByTicketNumber(String ticketNumber);

    Booking update(Booking expected, long customerId, long eventId, BookingStatus next);

    boolean delete(Booking expected);

    BookingStatistics statistics(BigDecimal threshold);
}
