package ru.ticketpoint;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import ru.ticketpoint.config.ConnectionProvider;
import ru.ticketpoint.exception.BookingException;
import ru.ticketpoint.exception.EntityNotFoundException;
import ru.ticketpoint.model.Booking;
import ru.ticketpoint.model.BookingStatus;
import ru.ticketpoint.model.Customer;
import ru.ticketpoint.model.CreateCustomerCommand;
import ru.ticketpoint.model.CreateEventCommand;
import ru.ticketpoint.model.Event;
import ru.ticketpoint.repository.JdbcBookingRepository;
import ru.ticketpoint.repository.JdbcCustomerRepository;
import ru.ticketpoint.repository.JdbcDatabaseMetadataRepository;
import ru.ticketpoint.repository.JdbcEventRepository;
import ru.ticketpoint.service.BookingService;
import ru.ticketpoint.service.CustomerService;
import ru.ticketpoint.service.DatabaseMetadataService;
import ru.ticketpoint.service.EventService;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TEST_DB_USER", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TEST_DB_PASSWORD", matches = ".+")
class PostgresIntegrationTest {
    private String schema;
    private ConnectionProvider connections;
    private JdbcBookingRepository bookingRepository;
    private JdbcCustomerRepository customerRepository;
    private JdbcEventRepository eventRepository;
    private CustomerService customers;
    private EventService events;
    private DatabaseMetadataService metadata;
    private BookingService service;

    private Connection rootConnection() throws Exception {
        return DriverManager.getConnection(System.getenv("TEST_DB_URL"),
                System.getenv("TEST_DB_USER"), System.getenv("TEST_DB_PASSWORD"));
    }

    @BeforeEach
    void prepare() throws Exception {
        schema = "test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection c = rootConnection(); var ps = c.prepareStatement("CREATE SCHEMA " + schema)) {
            ps.execute();
        }
        connections = () -> {
            var c = DriverManager.getConnection(System.getenv("TEST_DB_URL"),
                    System.getenv("TEST_DB_USER"), System.getenv("TEST_DB_PASSWORD"));
            c.setSchema(schema);
            return c;
        };
        try (var c = connections.open(); var ps = c.prepareStatement(Files.readString(Path.of("db/schema.sql")))) {
            ps.execute();
        }
        seed();
        bookingRepository = new JdbcBookingRepository(connections);
        customerRepository = new JdbcCustomerRepository(connections);
        eventRepository = new JdbcEventRepository(connections);
        customers = new CustomerService(customerRepository);
        events = new EventService(eventRepository, Clock.systemDefaultZone());
        metadata = new DatabaseMetadataService(new JdbcDatabaseMetadataRepository(connections));
        service = new BookingService(bookingRepository, customerRepository, eventRepository,
                Clock.systemDefaultZone());
    }

    private void seed() throws Exception {
        try (var c = connections.open(); var ps = c.prepareStatement(Files.readString(Path.of("db/seed.sql")))) {
            ps.execute();
        }
    }

    @AfterEach
    void cleanup() throws Exception {
        if (schema != null) {
            try (Connection c = rootConnection(); var ps = c.prepareStatement("DROP SCHEMA " + schema + " CASCADE")) {
                ps.execute();
            }
        }
    }

    @Test
    void seedIsRepeatableAndSearchFiltersSortStatisticsWork() throws Exception {
        seed();
        assertEquals(5, customers.findAll().size());
        assertEquals(3, events.findAll().size());
        assertEquals(10, service.statistics().total());
        assertEquals(4, service.statistics().newBookings());
        assertEquals(3, service.statistics().paid());
        assertEquals(3, service.statistics().cancelled());
        assertEquals(6, service.statistics().expensiveTickets());
        assertEquals("TP-DEMO-001", service.findByTicketNumber("tp-demo-001").getTicketNumber());
        assertEquals(4, service.searchByEventTitle("Джазовый").size());
        assertEquals(4, service.searchByEventTitle("джазовый").size());
        assertTrue(service.searchByEventTitle("%").isEmpty());
        assertEquals(3, service.filterByStatus(BookingStatus.PAID).size());
        assertEquals(2, service.filterByCustomer(customers.findAll().get(0).getId()).size());
        assertEquals("TP-DEMO-010", service.sortByCreatedAt().get(0).getTicketNumber());
        var sorted = service.sortByEventDate();
        for (int i = 1; i < sorted.size(); i++) {
            assertFalse(sorted.get(i).getEvent().getStartsAt().isBefore(sorted.get(i - 1).getEvent().getStartsAt()));
        }
        assertEquals(3, metadata.tableNames().size());
    }

    @Test
    void crudReusesCatalogAndNeverRenamesCustomer() {
        Customer customer = customers.findAll().get(0);
        Event event = events.findAll().get(0);
        Booking booking = service.createBooking(customer.getId(), event.getId());
        assertEquals(5, customers.findAll().size());
        assertEquals(3, events.findAll().size());
        assertEquals(event.getId(), booking.getEvent().getId());
        assertEquals(booking.getId(), service.findByTicketNumber(booking.getTicketNumber()).getId());
        assertThrows(BookingException.class, () -> customerRepository.create(
                new CreateCustomerCommand("Другое имя"), customer.getCustomerCode()));
        assertEquals(customer.getName(), customers.findById(customer.getId()).getName());
        assertEquals(BookingStatus.PAID, service.changeStatus(booking.getId(), BookingStatus.PAID).getStatus());
        assertThrows(BookingException.class, () -> service.changeStatus(booking.getId(), BookingStatus.NEW));
        assertEquals(BookingStatus.CANCELLED, service.changeStatus(booking.getId(), BookingStatus.CANCELLED).getStatus());
        assertThrows(BookingException.class, () -> service.changeStatus(booking.getId(), BookingStatus.PAID));
        service.delete(booking.getId());
        assertThrows(EntityNotFoundException.class, () -> service.findById(booking.getId()));
        assertThrows(EntityNotFoundException.class, () -> service.delete(booking.getId()));
    }

    @Test
    void missingReferencesAndPastEventsAreRejectedWithoutPartialWrites() throws Exception {
        long customerId = customers.findAll().get(0).getId();
        long eventId = events.findAll().get(0).getId();
        assertThrows(EntityNotFoundException.class, () -> service.createBooking(Long.MAX_VALUE, eventId));
        assertThrows(EntityNotFoundException.class, () -> service.createBooking(customerId, Long.MAX_VALUE));
        assertThrows(BookingException.class, () -> bookingRepository.create(
                Long.MAX_VALUE, eventId, "TP-NO-CUSTOMER"));
        assertEquals(10, service.findAll().size());
        Clock future = Clock.fixed(Instant.now().plusSeconds(100L * 86400), ZoneId.systemDefault());
        BookingService later = new BookingService(bookingRepository, customerRepository, eventRepository, future);
        assertThrows(BookingException.class, () -> later.createBooking(customerId, eventId));
        EventService laterEvents = new EventService(eventRepository, Clock.systemDefaultZone());
        assertThrows(BookingException.class, () -> laterEvents.create(new CreateEventCommand("Old", "Hall",
                LocalDateTime.now().minusDays(1), BigDecimal.ONE)));
        assertEquals(10, service.findAll().size());
    }

    @Test
    void concurrentStatusChangesHaveExactlyOneWinner() throws Exception {
        Booking booking = service.createBooking(customers.findAll().get(0).getId(), events.findAll().get(0).getId());
        CyclicBarrier barrier = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> updateAfterBarrier(barrier, booking.getId(), BookingStatus.PAID));
            var second = pool.submit(() -> updateAfterBarrier(barrier, booking.getId(), BookingStatus.CANCELLED));
            int successes = first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS);
            assertEquals(1, successes);
            assertNotEquals(BookingStatus.NEW, service.findById(booking.getId()).getStatus());
        } finally {
            pool.shutdownNow();
        }
    }

    private int updateAfterBarrier(CyclicBarrier barrier, long id, BookingStatus next) throws Exception {
        barrier.await(5, TimeUnit.SECONDS);
        try {
            bookingRepository.updateStatus(id, BookingStatus.NEW, next);
            return 1;
        } catch (BookingException e) {
            assertTrue(e.getMessage().contains("другим пользователем"));
            return 0;
        }
    }

    @Test
    void bookingKeepsPriceSnapshotWhenEventPriceChanges() throws Exception {
        Customer customer = customers.findAll().get(0);
        Event event = events.findAll().get(0);
        Booking created = service.createBooking(customer.getId(), event.getId());
        assertEquals(event.getPrice(), created.getPriceAtBooking());

        try (var c = connections.open(); var ps = c.prepareStatement("UPDATE events SET price = ? WHERE id = ?")) {
            ps.setBigDecimal(1, new BigDecimal("9999.00"));
            ps.setLong(2, event.getId());
            assertEquals(1, ps.executeUpdate());
        }

        assertEquals(event.getPrice(), service.findById(created.getId()).getPriceAtBooking());
    }

    @Test
    void eventPriceRoundTripsWithoutRounding() {
        Event saved = events.create(new CreateEventCommand("Новый концерт", "Зал",
                LocalDateTime.now().plusDays(7), new BigDecimal("12.340")));
        assertEquals(new BigDecimal("12.34"), saved.getPrice());
        assertEquals(saved.getPrice(), events.findById(saved.getId()).getPrice());
    }

    @Test
    void databaseConstraintsRejectInvalidDirectWritesAndReferencedDeletes() throws Exception {
        try (var c = connections.open(); var ps = c.prepareStatement(
                "INSERT INTO customers(customer_code, name) VALUES (?, ?)")) {
            ps.setString(1, "INVALID-CODE");
            ps.setString(2, "Клиент");
            SQLException error = assertThrows(SQLException.class, ps::executeUpdate);
            assertEquals("23514", error.getSQLState());
        }

        Customer customer = customers.findAll().get(0);
        Event event = events.findAll().get(0);
        service.createBooking(customer.getId(), event.getId());
        try (var c = connections.open(); var ps = c.prepareStatement("DELETE FROM customers WHERE id = ?")) {
            ps.setLong(1, customer.getId());
            SQLException error = assertThrows(SQLException.class, ps::executeUpdate);
            assertEquals("23001", error.getSQLState());
        }
    }

}
