package ru.ticketpoint.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import ru.ticketpoint.auth.AuthException;
import ru.ticketpoint.exception.BookingException;
import ru.ticketpoint.exception.EntityNotFoundException;
import ru.ticketpoint.model.Booking;
import ru.ticketpoint.model.BookingSort;
import ru.ticketpoint.model.BookingStatistics;
import ru.ticketpoint.model.BookingStatus;
import ru.ticketpoint.model.Event;
import ru.ticketpoint.model.Validation;
import ru.ticketpoint.repository.BookingRepository;

public final class BookingService {
    private static final BigDecimal EXPENSIVE_THRESHOLD = new BigDecimal("5000.00");
    private final BookingRepository bookings;
    private final CustomerService customers;
    private final EventService events;
    private final Clock clock;

    private final AccessPolicy access;

    public BookingService(BookingRepository bookings, CustomerService customers,
                          EventService events, Clock clock) {
        this(bookings, customers, events, clock, AccessPolicy.TRUSTED);
    }

    public BookingService(BookingRepository bookings, CustomerService customers,
                          EventService events, Clock clock, AccessPolicy access) {
        this.access = Objects.requireNonNull(access);
        this.bookings = Objects.requireNonNull(bookings);
        this.customers = Objects.requireNonNull(customers);
        this.events = Objects.requireNonNull(events);
        this.clock = Objects.requireNonNull(clock);
    }

    public Booking createBooking(long customerId, long eventId) {
        access.requireCustomer(customerId);
        if (!access.personal()) access.write().run();
        customers.findById(customerId);
        Event event = events.findById(eventId);
        requireAvailable(event);
        String number = "TP-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
        return bookings.create(customerId, eventId, number);
    }

    private void requireAvailable(Event event) {
        if (!event.active())
            throw new BookingException("Это мероприятие больше недоступно для бронирования. Выберите другое.");
        EventSchedule.requireFuture(event.startsAt(), clock);
    }

    public List<Booking> findAll() {
        access.read().run();
        return access.personal() ? bookings.findByCustomerId(access.requireCustomerId()) : bookings.findAll();
    }

    public Booking findById(long id) {
        access.read().run();
        Booking booking = bookings.findById(Validation.id(id))
                .orElseThrow(() -> new EntityNotFoundException("Бронирование не найдено"));
        access.requireCustomer(booking.customer().id());
        return booking;
    }

    public Booking findByTicketNumber(String number) {
        access.read().run();
        String normalized = Validation.text(number, "Номер билета", 40).toUpperCase(Locale.ROOT);
        Booking booking = bookings.findByTicketNumber(normalized)
                .orElseThrow(() -> new EntityNotFoundException("Билет не найден"));
        access.requireCustomer(booking.customer().id());
        return booking;
    }

    public List<Booking> searchByEventTitle(String title) {
        access.read().run();
        String query = Validation.text(title, "Название мероприятия", 200);
        return visible(bookings.findByEventTitle(query));
    }

    public Booking changeStatus(long id, BookingStatus next) {
        Booking current = findById(id);
        authorizeChange(current, next);
        BookingStatus expected = current.status();
        expected.validateTransitionTo(next);
        return bookings.update(current, current.customer().id(), current.event().id(), next);
    }

    public Booking updateBooking(Booking expected, long customerId, long eventId, BookingStatus next) {
        if (!access.personal()) access.write().run();
        Objects.requireNonNull(expected, "Исходное бронирование обязательно");
        Booking current = findById(Objects.requireNonNull(expected.id()));
        access.requireCustomer(customerId);
        authorizeChange(current, next);
        current.requireUnchanged(expected);
        current.status().validateTransitionTo(next);
        boolean detailsChanged = customerId != current.customer().id() || eventId != current.event().id();
        if (detailsChanged) {
            if (current.status() != BookingStatus.NEW) {
                throw new BookingException("Клиента и мероприятие можно менять только у нового бронирования. "
                        + "Для оплаченного билета доступна отмена");
            }
            EventSchedule.requireFuture(current.event().startsAt(), clock);
            customers.findById(customerId);
            Event selected = events.findById(eventId);
            requireAvailable(selected);
        }
        return bookings.update(current, customerId, eventId, next);
    }

    private void authorizeChange(Booking current, BookingStatus next) {
        access.requireCustomer(current.customer().id());
        if (access.personal()) {
            if (!(current.status() == BookingStatus.NEW && next == BookingStatus.NEW
                    || (current.status() == BookingStatus.NEW || current.status() == BookingStatus.CONFIRMED)
                    && next == BookingStatus.CANCELLED))
                throw new AuthException("Подтверждение и отметка оплаты доступны менеджеру. "
                        + "Можно изменить новую заявку или отменить свою неоплаченную заявку", true);
            EventSchedule.requireFuture(current.event().startsAt(), clock);
        } else access.write().run();
        if (next == BookingStatus.CONFIRMED || next == BookingStatus.PAID)
            EventSchedule.requireFuture(current.event().startsAt(), clock);
    }

    public void delete(long id) {
        if (!access.personal()) access.write().run();
        Booking current = findById(id);
        if (current.status() != BookingStatus.NEW && (access.personal() || current.status() != BookingStatus.CANCELLED))
            throw new BookingException("Удалить можно новую заявку" + (access.personal() ? "" : " или отменённую запись")
                    + ". Подтверждённые и оплаченные билеты сначала отменяются менеджером");
        if (!bookings.delete(current)) throw new BookingException("Бронирование уже изменено. Обновите список");
    }

    public List<Booking> filterByStatus(BookingStatus status) {
        access.read().run();
        if (status == null) {
            throw new BookingException("Статус обязателен");
        }
        return visible(bookings.findByStatus(status));
    }

    public List<Booking> filterByCustomer(long id) {
        access.read().run();
        customers.findById(id);
        access.requireCustomer(id);
        return bookings.findByCustomerId(id);
    }

    public List<Booking> sortByCreatedAt() {
        access.read().run();
        return visible(bookings.findAll(BookingSort.CREATED_AT_DESC));
    }

    public List<Booking> sortByEventDate() {
        access.read().run();
        return visible(bookings.findAll(BookingSort.EVENT_DATE_ASC));
    }

    public BookingStatistics statistics() {
        access.read().run();
        if (!access.personal()) return bookings.statistics(EXPENSIVE_THRESHOLD);
        List<Booking> own = findAll();
        return new BookingStatistics(own.size(), own.stream().filter(b -> b.status() == BookingStatus.NEW).count(),
                own.stream().filter(b -> b.status() == BookingStatus.CONFIRMED).count(),
                own.stream().filter(b -> b.status() == BookingStatus.PAID).count(),
                own.stream().filter(b -> b.status() == BookingStatus.CANCELLED).count(),
                own.stream().filter(b -> b.priceAtBooking().compareTo(EXPENSIVE_THRESHOLD) >= 0).count());
    }

    private List<Booking> visible(List<Booking> rows) {
        return access.personal() ? rows.stream().filter(b -> b.customer().id().equals(access.requireCustomerId())).toList() : rows;
    }
}
