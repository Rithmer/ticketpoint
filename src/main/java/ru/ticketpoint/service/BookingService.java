package ru.ticketpoint.service;

import ru.ticketpoint.exception.BookingException;
import ru.ticketpoint.exception.EntityNotFoundException;
import ru.ticketpoint.model.Booking;
import ru.ticketpoint.model.BookingStatistics;
import ru.ticketpoint.model.BookingStatus;
import ru.ticketpoint.model.BookingSort;
import ru.ticketpoint.model.Customer;
import ru.ticketpoint.model.Event;
import ru.ticketpoint.model.Validation;
import ru.ticketpoint.repository.BookingRepository;
import ru.ticketpoint.repository.CustomerRepository;
import ru.ticketpoint.repository.EventRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public final class BookingService {
    private static final BigDecimal EXPENSIVE_THRESHOLD = new BigDecimal("5000.00");
    private final BookingRepository bookings;
    private final CustomerRepository customers;
    private final EventRepository events;
    private final Clock clock;

    public BookingService(BookingRepository bookings, CustomerRepository customers,
                          EventRepository events, Clock clock) {
        this.bookings = Objects.requireNonNull(bookings);
        this.customers = Objects.requireNonNull(customers);
        this.events = Objects.requireNonNull(events);
        this.clock = Objects.requireNonNull(clock);
    }

    public Booking createBooking(long customerId, long eventId) {
        customer(customerId);
        Event event = event(eventId);
        if (!event.getStartsAt().isAfter(LocalDateTime.now(clock))) {
            throw new BookingException("Мероприятие должно быть в будущем");
        }
        String number = "TP-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
        return bookings.create(customerId, eventId, number);
    }

    public List<Booking> findAll() { return bookings.findAll(); }

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
        BookingStatus expected = current.getStatus();
        current.changeStatus(next);
        return bookings.updateStatus(id, expected, current.getStatus());
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
        customer(id);
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

    private Customer customer(long id) {
        return customers.findById(Validation.id(id))
                .orElseThrow(() -> new EntityNotFoundException("Клиент не найден"));
    }

    private Event event(long id) {
        return events.findById(Validation.id(id))
                .orElseThrow(() -> new EntityNotFoundException("Мероприятие не найдено"));
    }
}
