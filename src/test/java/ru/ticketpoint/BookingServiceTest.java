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
                case "updateStatus" -> {
                    assertEquals(BookingStatus.NEW, args[1]);
                    assertEquals(BookingStatus.PAID, args[2]);
                    throw failure;
                }
                default -> throw new AssertionError("Неожиданный вызов: " + method.getName());
        });
        BookingService service = service(repository, event);

        assertSame(failure, assertThrows(DataAccessException.class,
                () -> service.changeStatus(booking.id(), BookingStatus.PAID)));
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
