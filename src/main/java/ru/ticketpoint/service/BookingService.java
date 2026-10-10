package ru.ticketpoint.service;

import ru.ticketpoint.exception.BookingException;
import ru.ticketpoint.exception.EntityNotFoundException;
import ru.ticketpoint.model.Booking;
import ru.ticketpoint.model.BookingStatistics;
import ru.ticketpoint.model.BookingStatus;
import ru.ticketpoint.model.BookingSort;
import ru.ticketpoint.model.Event;
import ru.ticketpoint.model.Validation;
import ru.ticketpoint.repository.BookingRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public final class BookingService {
    private static final BigDecimal EXPENSIVE_THRESHOLD = new BigDecimal("5000.00");
    private final BookingRepository bookings;
    private final CustomerService customers;
    private final EventService events;
    private final Clock clock;

    public BookingService(BookingRepository bookings, CustomerService customers,
                          EventService events, Clock clock) {
        this.bookings = Objects.requireNonNull(bookings);
        this.customers = Objects.requireNonNull(customers);
        this.events = Objects.requireNonNull(events);
        this.clock = Objects.requireNonNull(clock);
    }

    public Booking createBooking(long customerId, long eventId) {
        customers.findById(customerId);
        Event event = events.findById(eventId);
        EventSchedule.requireFuture(event.startsAt(), clock);
        String number = "TP-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
        return bookings.create(customerId, eventId, number);
    }

    public List<Booking> findAll() {
        return bookings.findAll();
    }

    public Booking findById(long id) {
        return bookings.findById(Validation.id(id))
                .orElseThrow(() -> new EntityNotFoundException("Бронирование не найдено"));
    }

    public Booking findByTicketNumber(String number) {
        String normalized = Validation.text(number, "Номер билета", 40).toUpperCase(Locale.ROOT);
        return bookings.findByTicketNumber(normalized)
                .orElseThrow(() -> new EntityNotFoundException("Билет не найден"));
    }

    public List<Booking> searchByEventTitle(String title) {
        String query = Validation.text(title, "Название мероприятия", 200);
        return bookings.findByEventTitle(query);
    }

    public Booking changeStatus(long id, BookingStatus next) {
        Booking current = findById(id);
        BookingStatus expected = current.status();
        expected.validateTransitionTo(next);
        return bookings.updateStatus(id, expected, next);
    }

    public void delete(long id) {
        if (!bookings.delete(Validation.id(id))) {
            throw new EntityNotFoundException("Бронирование не найдено");
        }
    }

    public List<Booking> filterByStatus(BookingStatus status) {
        if (status == null) {
            throw new BookingException("Статус обязателен");
        }
        return bookings.findByStatus(status);
    }

    public List<Booking> filterByCustomer(long id) {
        customers.findById(id);
        return bookings.findByCustomerId(id);
    }

    public List<Booking> sortByCreatedAt() {
        return bookings.findAll(BookingSort.CREATED_AT_DESC);
    }

    public List<Booking> sortByEventDate() {
        return bookings.findAll(BookingSort.EVENT_DATE_ASC);
    }

    public BookingStatistics statistics() {
        return bookings.statistics(EXPENSIVE_THRESHOLD);
    }

}
