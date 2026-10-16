package ru.ticketpoint;

import org.junit.jupiter.api.Test;
import ru.ticketpoint.exception.BookingException;
import ru.ticketpoint.exception.DataAccessException;
import ru.ticketpoint.model.Booking;
import ru.ticketpoint.model.BookingStatus;
import ru.ticketpoint.model.CreateEventCommand;
import ru.ticketpoint.model.Customer;
import ru.ticketpoint.model.Event;
import ru.ticketpoint.repository.BookingRepository;
import ru.ticketpoint.repository.CustomerRepository;
import ru.ticketpoint.repository.EventRepository;
import ru.ticketpoint.service.BookingService;
import ru.ticketpoint.service.CustomerService;
import ru.ticketpoint.service.EventService;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BookingServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-01-01T12:00:00Z"), ZoneOffset.UTC);
    private final Customer customer = new Customer(1L, "CUS-00000000-0000-0000-0000-000000000001", "Иван");
    private final Event event = new Event(2L, "Концерт", "Зал", LocalDateTime.now(CLOCK).plusDays(1), BigDecimal.TEN);

    @Test
    void failedStatusUpdateDoesNotMutateLoadedBooking() {
        Booking booking = booking(BookingStatus.NEW);
        DataAccessException failure = new DataAccessException("Ошибка сохранения", null);
        BookingRepository repository = stub(BookingRepository.class, (proxy, method, args) -> switch (method.getName()) {
                case "findById" -> Optional.of(booking);
                case "update" -> {
                    assertEquals(BookingStatus.NEW, ((Booking) args[0]).status());
                    assertEquals(BookingStatus.CONFIRMED, args[3]);
                    throw failure;
                }
                default -> throw new AssertionError("Неожиданный вызов: " + method.getName());
        });
        BookingService service = service(repository, event);

        assertSame(failure, assertThrows(DataAccessException.class,
                () -> service.changeStatus(booking.id(), BookingStatus.CONFIRMED)));
        assertEquals(BookingStatus.NEW, booking.status());
    }

    @Test
    void forbiddenStatusTransitionDoesNotReachRepositoryUpdate() {
        Booking booking = booking(BookingStatus.PAID);
        BookingRepository repository = stub(BookingRepository.class, (proxy, method, args) -> {
            if (method.getName().equals("findById")) {
                return Optional.of(booking);
            }
            throw new AssertionError("Неожиданный вызов: " + method.getName());
        });

        assertThrows(BookingException.class,
                () -> service(repository, event).changeStatus(booking.id(), BookingStatus.NEW));
        assertEquals(BookingStatus.PAID, booking.status());
    }

    @Test
    void bothCreationOperationsRejectEventsAtOrBeforeCurrentTime() {
        AtomicInteger writes = new AtomicInteger();
        BookingRepository bookings = stub(BookingRepository.class, (proxy, method, args) -> {
            writes.incrementAndGet();
            throw new AssertionError("Запись запрещена для прошедшего мероприятия");
        });
        EventRepository events = stub(EventRepository.class, (proxy, method, args) -> {
            writes.incrementAndGet();
            throw new AssertionError("Запись запрещена для прошедшего мероприятия");
        });
        EventService eventService = new EventService(events, CLOCK);
        for (LocalDateTime date : new LocalDateTime[]{LocalDateTime.now(CLOCK), LocalDateTime.now(CLOCK).minusSeconds(1)}) {
            Event pastEvent = new Event(2L, "Концерт", "Зал", date, BigDecimal.TEN);
            assertThrows(BookingException.class, () -> service(bookings, pastEvent).createBooking(1L, 2L));
            assertThrows(BookingException.class,
                    () -> eventService.create(new CreateEventCommand("Концерт", "Зал", date, BigDecimal.TEN)));
        }
        assertEquals(0, writes.get());
    }

    @Test
    void newBookingCanChangeCustomerAndEventWithoutChangingStatus() {
        Booking original = booking(BookingStatus.NEW);
        AtomicInteger updates = new AtomicInteger();
        Event replacement = new Event(4L, "Премьера", "Театр", event.startsAt().plusDays(1), new BigDecimal("1500"));
        BookingRepository repository = stub(BookingRepository.class, (proxy, method, args) -> switch (method.getName()) {
            case "findById" -> Optional.of(original);
            case "update" -> {
                assertSame(original, args[0]);
                assertEquals(5L, args[1]);
                assertEquals(4L, args[2]);
                assertEquals(BookingStatus.NEW, args[3]);
                updates.incrementAndGet();
                yield original;
            }
            default -> throw new AssertionError(method.getName());
        });
        service(repository, replacement).updateBooking(original, 5L, 4L, BookingStatus.NEW);
        assertEquals(1, updates.get());
    }

    @Test
    void paidAndCancelledBookingsCannotChangeTheirDetails() {
        for (BookingStatus status : new BookingStatus[]{BookingStatus.PAID, BookingStatus.CANCELLED}) {
            Booking original = booking(status);
            BookingRepository repository = readOnlyRepository(original);
            assertThrows(BookingException.class,
                    () -> service(repository, event).updateBooking(original, 5L, event.id(), status));
        }
    }

    @Test
    void paidBookingCanBeCancelledWithoutChangingDetails() {
        Booking original = booking(BookingStatus.PAID);
        AtomicInteger updates = new AtomicInteger();
        BookingRepository repository = stub(BookingRepository.class, (proxy, method, args) -> switch (method.getName()) {
            case "findById" -> Optional.of(original);
            case "update" -> {
                assertEquals(BookingStatus.CANCELLED, args[3]);
                updates.incrementAndGet();
                yield original;
            }
            default -> throw new AssertionError(method.getName());
        });
        service(repository, event).updateBooking(original, customer.id(), event.id(), BookingStatus.CANCELLED);
        assertEquals(1, updates.get());
    }

    @Test
    void editingRejectsPastOriginalAndReplacementEvents() {
        Event past = new Event(4L, "Прошедший концерт", "Зал", LocalDateTime.now(CLOCK), BigDecimal.TEN);
        Booking original = booking(BookingStatus.NEW);
        assertThrows(BookingException.class, () -> service(readOnlyRepository(original), past)
                .updateBooking(original, customer.id(), past.id(), BookingStatus.NEW));
        Booking expired = new Booking(original.id(), original.ticketNumber(), customer, past,
                original.priceAtBooking(), BookingStatus.NEW, original.createdAt());
        assertThrows(BookingException.class, () -> service(readOnlyRepository(expired), event)
                .updateBooking(expired, customer.id(), event.id(), BookingStatus.NEW));
    }

    @Test
    void staleFormCannotOverwriteAnotherEditEvenWhenStatusIsUnchanged() {
        Booking original = booking(BookingStatus.NEW);
        Customer changedCustomer = new Customer(5L, "CUS-00000000-0000-0000-0000-000000000005", "Анна");
        Booking changed = new Booking(original.id(), original.ticketNumber(), changedCustomer, event,
                original.priceAtBooking(), original.status(), original.createdAt());
        BookingException error = assertThrows(BookingException.class, () -> service(readOnlyRepository(changed), event)
                .updateBooking(original, customer.id(), event.id(), BookingStatus.PAID));
        assertTrue(error.getMessage().contains("другим пользователем"));
    }

    @Test
    void updateRequiresWritePermissionBeforeReadingOrSaving() {
        BookingRepository repository = stub(BookingRepository.class, (proxy, method, args) -> {
            throw new AssertionError("Нет доступа к данным без права записи");
        });
        var denied = new ru.ticketpoint.service.AccessPolicy(() -> { }, () -> {
            throw new BookingException("Нет права записи");
        });
        BookingService service = new BookingService(repository, unusedCustomerService(),
                new EventService(stub(EventRepository.class, (p, m, a) -> null), CLOCK), CLOCK, denied);
        assertThrows(BookingException.class,
                () -> service.updateBooking(booking(BookingStatus.NEW), 1L, 2L, BookingStatus.NEW));
    }

    private CustomerService unusedCustomerService() {
        return new CustomerService(stub(CustomerRepository.class, (p, m, a) -> null));
    }

    private BookingRepository readOnlyRepository(Booking booking) {
        return stub(BookingRepository.class, (proxy, method, args) -> {
            if (method.getName().equals("findById")) return Optional.of(booking);
            throw new AssertionError("Недопустимая запись: " + method.getName());
        });
    }

    private Booking booking(BookingStatus status) {
        return new Booking(3L, "TP-TEST", customer, event, BigDecimal.TEN, status, LocalDateTime.now(CLOCK));
    }

    private BookingService service(BookingRepository bookings, Event selectedEvent) {
        CustomerRepository customers = stub(CustomerRepository.class, (proxy, method, args) -> {
            if (method.getName().equals("findById")) return Optional.of(customer);
            throw new AssertionError("Неожиданный вызов: " + method.getName());
        });
        EventRepository events = stub(EventRepository.class, (proxy, method, args) -> {
            if (method.getName().equals("findById")) return Optional.of(selectedEvent);
            throw new AssertionError("Неожиданный вызов: " + method.getName());
        });
        return new BookingService(bookings, new CustomerService(customers), new EventService(events, CLOCK), CLOCK);
    }

    private static <T> T stub(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
