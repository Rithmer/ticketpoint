package ru.ticketpoint;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
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
        service = new BookingService(bookingRepository, customers, events,
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
        assertTrue(service.findAll().stream().allMatch(b -> b.ticketNumber().matches(
                "TP-[0-9A-F]{8}-[0-9A-F]{4}-4[0-9A-F]{3}-[89AB][0-9A-F]{3}-[0-9A-F]{12}")));
        assertTrue(customers.findAll().stream().allMatch(c -> c.customerCode().matches(
                "CUS-[0-9A-F]{8}-[0-9A-F]{4}-4[0-9A-F]{3}-[89AB][0-9A-F]{3}-[0-9A-F]{12}")));
        assertTrue(events.findAll().stream().noneMatch(e -> e.title().startsWith("Демо:")));
        assertEquals("TP-B3D9B984-FCC3-4A40-9239-C764D976EC8E", service.findByTicketNumber("tp-b3d9b984-fcc3-4a40-9239-c764d976ec8e").ticketNumber());
        assertEquals(4, service.searchByEventTitle("Джазовый").size());
        assertEquals(4, service.searchByEventTitle("джазовый").size());
        assertTrue(service.searchByEventTitle("%").isEmpty());
        assertEquals(3, service.filterByStatus(BookingStatus.PAID).size());
        assertEquals(2, service.filterByCustomer(customers.findAll().getFirst().id()).size());
        assertEquals("TP-74B5C567-21F3-4CC8-AE4C-9968CAD65A40", service.sortByCreatedAt().getFirst().ticketNumber());
        var sorted = service.sortByEventDate();
        for (int i = 1; i < sorted.size(); i++) {
            assertFalse(sorted.get(i).event().startsAt().isBefore(sorted.get(i - 1).event().startsAt()));
        }
        assertEquals(3, metadata.tableNames().size());
    }

    @Test
    void crudReusesCatalogAndNeverRenamesCustomer() {
        Customer customer = customers.findAll().getFirst();
        Event event = events.findAll().getFirst();
        Booking booking = service.createBooking(customer.id(), event.id());
        assertEquals(5, customers.findAll().size());
        assertEquals(3, events.findAll().size());
        assertEquals(event.id(), booking.event().id());
        assertEquals(booking.id(), service.findByTicketNumber(booking.ticketNumber()).id());
        assertThrows(BookingException.class, () -> customerRepository.create(
                new CreateCustomerCommand("Другое имя"), customer.customerCode()));
        assertEquals(customer.name(), customers.findById(customer.id()).name());
        assertEquals(BookingStatus.PAID, service.changeStatus(booking.id(), BookingStatus.PAID).status());
        assertThrows(BookingException.class, () -> service.changeStatus(booking.id(), BookingStatus.NEW));
        assertEquals(BookingStatus.CANCELLED, service.changeStatus(booking.id(), BookingStatus.CANCELLED).status());
        assertThrows(BookingException.class, () -> service.changeStatus(booking.id(), BookingStatus.PAID));
        service.delete(booking.id());
        assertThrows(EntityNotFoundException.class, () -> service.findById(booking.id()));
        assertThrows(EntityNotFoundException.class, () -> service.delete(booking.id()));
    }

    @Test
    void missingReferencesAndPastEventsAreRejectedWithoutPartialWrites() {
        long customerId = customers.findAll().getFirst().id();
        long eventId = events.findAll().getFirst().id();
        assertThrows(EntityNotFoundException.class, () -> service.createBooking(Long.MAX_VALUE, eventId));
        assertThrows(EntityNotFoundException.class, () -> service.createBooking(customerId, Long.MAX_VALUE));
        assertThrows(BookingException.class, () -> bookingRepository.create(
                Long.MAX_VALUE, eventId, "TP-NO-CUSTOMER"));
        assertEquals(10, service.findAll().size());
        Clock future = Clock.fixed(Instant.now().plusSeconds(100L * 86400), ZoneId.systemDefault());
        BookingService later = new BookingService(bookingRepository, customers, new EventService(eventRepository, future), future);
        assertThrows(BookingException.class, () -> later.createBooking(customerId, eventId));
        EventService laterEvents = new EventService(eventRepository, Clock.systemDefaultZone());
        assertThrows(BookingException.class, () -> laterEvents.create(new CreateEventCommand("Old", "Hall",
                LocalDateTime.now().minusDays(1), BigDecimal.ONE)));
        assertEquals(10, service.findAll().size());
    }

    @Test
    void concurrentStatusChangesHaveExactlyOneWinner() throws Exception {
        Booking booking = service.createBooking(customers.findAll().getFirst().id(), events.findAll().getFirst().id());
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> updateAfterBarrier(barrier, booking.id(), BookingStatus.PAID));
            var second = pool.submit(() -> updateAfterBarrier(barrier, booking.id(), BookingStatus.CANCELLED));
            int successes = first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS);
            assertEquals(1, successes);
            assertNotEquals(BookingStatus.NEW, service.findById(booking.id()).status());
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
        Customer customer = customers.findAll().getFirst();
        Event event = events.findAll().getFirst();
        Booking created = service.createBooking(customer.id(), event.id());
        assertEquals(event.price(), created.priceAtBooking());

        try (var c = connections.open(); var ps = c.prepareStatement("UPDATE events SET price = ? WHERE id = ?")) {
            ps.setBigDecimal(1, new BigDecimal("9999.00"));
            ps.setLong(2, event.id());
            assertEquals(1, ps.executeUpdate());
        }

        assertEquals(event.price(), service.findById(created.id()).priceAtBooking());
    }

    @Test
    void eventPriceRoundTripsWithoutRounding() {
        Event saved = events.create(new CreateEventCommand("Новый концерт", "Зал",
                LocalDateTime.now().plusDays(7), new BigDecimal("12.340")));
        assertEquals(new BigDecimal("12.34"), saved.price());
        assertEquals(saved.price(), events.findById(saved.id()).price());
    }

    @Test
    void consoleExportFailureIsLoggedBeforeLoggingCloses(@TempDir Path directory) throws Exception {
        Path destination = Files.createDirectory(directory.resolve("existing.xlsx"));
        Files.writeString(destination.resolve("keep.txt"), "Keep this file");
        Path output = directory.resolve("console.txt");
        var command = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), ConsoleApplication.class.getName(),
                "--export", destination.toString());
        command.directory(directory.toFile()).redirectErrorStream(true).redirectOutput(output.toFile());
        String url = System.getenv("TEST_DB_URL");
        command.environment().put("DB_URL", url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema);
        command.environment().put("DB_USER", System.getenv("TEST_DB_USER"));
        command.environment().put("DB_PASSWORD", System.getenv("TEST_DB_PASSWORD"));
        Process process = command.start();
        try {
            process.getOutputStream().close();
            assertTrue(process.waitFor(20, TimeUnit.SECONDS), "Console command did not finish");
            assertEquals(1, process.exitValue());
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
        assertTrue(Files.readString(output).contains("Подробности в журнале"));
        String log = Files.readString(directory.resolve("logs/ticketpoint-0.log"));
        assertTrue(log.contains("Выполнение команды"));
        assertFalse(log.contains(System.getenv("TEST_DB_PASSWORD")));
        assertEquals("Keep this file", Files.readString(destination.resolve("keep.txt")));
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

        Customer customer = customers.findAll().getFirst();
        Event event = events.findAll().getFirst();
        service.createBooking(customer.id(), event.id());
        try (var c = connections.open(); var ps = c.prepareStatement("DELETE FROM customers WHERE id = ?")) {
            ps.setLong(1, customer.id());
            SQLException error = assertThrows(SQLException.class, ps::executeUpdate);
            assertEquals("23503", error.getSQLState());
        }
    }

}
