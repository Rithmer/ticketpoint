package ru.ticketpoint.auth;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import ru.ticketpoint.config.*;
import ru.ticketpoint.repository.JdbcUserRepository;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class AuthIntegrationTest {
    private static final String INITIAL = "Initial-password-2026!";
    private static final String PERMANENT = "Permanent-password-2026!";
    private String schema;
    private DatabaseConfig config;
    private AuthService auth;
    private User admin;
    private final MutableClock clock = new MutableClock();
    private Connection connection() throws SQLException {
        return DriverManager.getConnection(System.getenv("TEST_DB_URL"), System.getenv("TEST_DB_USER"), System.getenv("TEST_DB_PASSWORD"));
    }
    @BeforeEach void setup() throws Exception {
        schema = "auth_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var c = connection(); var s = c.createStatement()) {
            s.execute("CREATE SCHEMA " + schema); c.setSchema(schema);
            String schemaSql = Files.readString(Path.of("db/schema.sql"));
            String originalSchema = schemaSql.substring(0, schemaSql.indexOf("CREATE TABLE IF NOT EXISTS app_users"));
            s.execute(originalSchema);
            s.execute(Files.readString(Path.of("db/seed.sql")));
        }
        String url = System.getenv("TEST_DB_URL");
        config = new DatabaseConfig(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema,
                System.getenv("TEST_DB_USER"), System.getenv("TEST_DB_PASSWORD"), ZoneId.of("Europe/Moscow"), 5, 15, 30);
        auth = new AuthService(new JdbcUserRepository(new JdbcConnectionProvider(config)), clock);
        auth.initialize(new AuthConfig("admin", "Администратор", INITIAL));
        admin = auth.authenticate("ADMIN", INITIAL);
    }
    @AfterEach void cleanup() throws Exception {
        try (var c = connection(); var s = c.createStatement()) { s.execute("DROP SCHEMA " + schema + " CASCADE"); }
    }
    private void permanentAdmin() { admin = auth.changePassword(admin, INITIAL, PERMANENT); }
    @Test void upgradesExistingDatabaseWithoutChangingBookingsOrExistingPassword() {
        assertTrue(admin.passwordChangeRequired());
        assertThrows(AuthException.class, () -> auth.requireRead(admin));
        permanentAdmin(); auth.requireAdmin(admin);
        auth.initialize(new AuthConfig("different", "Other", null));
        assertEquals(admin.id(), auth.authenticate("admin", PERMANENT).id());
        assertEquals(10, ApplicationServices.secured(config, auth, () -> admin).bookings().findAll().size());
        assertThrows(AuthException.class, () -> auth.authenticate("admin", INITIAL));
    }
    @Test void enforcesRolesInServicesAndRevokesSessionsAfterPasswordChangeAndDisable() {
        permanentAdmin();
        User viewer = auth.create(admin, "observer", "Наблюдатель", INITIAL, Role.USER);
        viewer = auth.changePassword(auth.authenticate(viewer.login(), INITIAL), INITIAL, PERMANENT);
        User session = viewer;
        var services = ApplicationServices.secured(config, auth, () -> session);
        assertEquals(0, services.bookings().findAll().size());
        assertThrows(AuthException.class, () -> services.bookings().delete(1));
        assertThrows(AuthException.class, () -> services.customers().create(new ru.ticketpoint.model.CreateCustomerCommand("Запрещено")));
        assertThrows(AuthException.class, () -> auth.list(session));
        User changed = auth.changePassword(session, PERMANENT, "Another-password-2026!");
        assertThrows(AuthException.class, () -> services.bookings().findAll());
        auth.setEnabled(admin, changed.id(), false);
        assertThrows(AuthException.class, () -> auth.current(changed));
        assertThrows(AuthException.class, () -> auth.authenticate(changed.login(), "Another-password-2026!"));
        auth.setEnabled(admin, changed.id(), true);
        assertNotNull(auth.authenticate(changed.login(), "Another-password-2026!"));
        assertThrows(IllegalArgumentException.class, () -> auth.setEnabled(admin, admin.id(), false));
    }
    @Test void managerCanChangeDomainDataButCannotCreateAccounts() {
        permanentAdmin();
        User manager = auth.create(admin, "manager", "Менеджер", INITIAL, Role.MANAGER);
        manager = auth.changePassword(auth.authenticate(manager.login(), INITIAL), INITIAL, PERMANENT);
        User session = manager;
        var services = ApplicationServices.secured(config, auth, () -> session);
        var customer = ApplicationServices.secured(config, auth, () -> admin).customers().create(new ru.ticketpoint.model.CreateCustomerCommand("Новый клиент"));
        var booking = services.bookings().createBooking(customer.id(), services.events().findAll().getFirst().id());
        services.bookings().changeStatus(booking.id(), ru.ticketpoint.model.BookingStatus.CONFIRMED);
        services.bookings().changeStatus(booking.id(), ru.ticketpoint.model.BookingStatus.PAID);
        assertThrows(ru.ticketpoint.exception.BookingException.class, () -> services.bookings().delete(booking.id()));
        services.bookings().changeStatus(booking.id(), ru.ticketpoint.model.BookingStatus.CANCELLED);
        services.bookings().delete(booking.id());
        assertThrows(AuthException.class, () -> auth.create(session, "intruder", "Intruder", INITIAL, Role.ADMIN));
    }
    @Test void locksConcurrentFailedAttemptsAndAllowsLoginAfterFiveMinutes() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = new java.util.ArrayList<Future<?>>();
            for (int i = 0; i < 5; i++) futures.add(executor.submit(() -> assertThrows(AuthException.class, () -> auth.authenticate("admin", "wrong"))));
            for (var future : futures) future.get(20, TimeUnit.SECONDS);
        }
        assertThrows(AuthException.class, () -> auth.authenticate("admin", INITIAL));
        clock.now = clock.now.plusSeconds(301);
        assertNotNull(auth.authenticate("admin", INITIAL));
    }
    @Test void rejectsDuplicateLoginInvalidInputAndDoesNotRevealHashes() throws Exception {
        permanentAdmin();
        assertThrows(IllegalArgumentException.class, () -> auth.create(admin, "ADMIN", "Duplicate", INITIAL, Role.MANAGER));
        assertThrows(IllegalArgumentException.class, () -> auth.create(admin, "mail@example.org", "Mail", INITIAL, Role.USER));
        assertThrows(IllegalArgumentException.class, () -> auth.create(admin, "missing-role", "Role", INITIAL, null));
        assertFalse(auth.list(admin).toString().contains("pbkdf2"));
        try (var c = new JdbcConnectionProvider(config).open(); var s = c.createStatement(); var r = s.executeQuery("SELECT password_hash FROM app_users")) {
            assertTrue(r.next()); assertTrue(r.getString(1).startsWith("pbkdf2-sha256$"));
            assertFalse(r.getString(1).contains(PERMANENT));
        }
    }
    @Test void selfRegistrationCreatesPermanentBuyerAndOwnProfileWithoutAdminSession() {
        User buyer = auth.register(" New.Buyer ", " Покупатель ", PERMANENT, PERMANENT);
        assertEquals("new.buyer", buyer.login()); assertEquals("Покупатель", buyer.name());
        assertEquals(Role.USER, buyer.role()); assertNotNull(buyer.customerId());
        assertFalse(buyer.passwordChangeRequired()); assertTrue(buyer.enabled());
        auth.requireRead(buyer);
        assertThrows(AuthException.class, () -> auth.requireAdmin(buyer));
        assertThrows(AuthException.class, () -> auth.requireWrite(buyer));
        assertEquals(buyer, auth.authenticate("NEW.BUYER", PERMANENT));
        var personal = ApplicationServices.secured(config, auth, () -> buyer);
        assertEquals(buyer.customerId(), personal.customers().findAll().getFirst().id());
        assertTrue(personal.bookings().findAll().isEmpty());
        var booking = personal.bookings().createBooking(buyer.customerId(), personal.events().findAll().getFirst().id());
        assertEquals(buyer.customerId(), booking.customer().id());
    }

    @Test void invalidAndConcurrentDuplicateRegistrationsDoNotLeaveOrphanProfiles() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> auth.register("buyer", "Имя", PERMANENT, "different"));
        assertThrows(IllegalArgumentException.class, () -> auth.register("bad@login", "Имя", PERMANENT, PERMANENT));
        assertThrows(IllegalArgumentException.class, () -> auth.register("buyer", " ", PERMANENT, PERMANENT));
        assertThrows(IllegalArgumentException.class, () -> auth.register("buyer", "Имя", "short", "short"));
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Integer> register = () -> {
                barrier.await(5, TimeUnit.SECONDS);
                try { auth.register("same-buyer", "Покупатель", PERMANENT, PERMANENT); return 1; }
                catch (IllegalArgumentException error) { assertTrue(error.getMessage().contains("занят")); return 0; }
            };
            var first = pool.submit(register); var second = pool.submit(register);
            assertEquals(1, first.get(20, TimeUnit.SECONDS) + second.get(20, TimeUnit.SECONDS));
        }
        assertThrows(IllegalArgumentException.class, () -> auth.register("SAME-BUYER", "Дубликат", PERMANENT, PERMANENT));
        try (var c = new JdbcConnectionProvider(config).open(); var s = c.createStatement();
             var rows = s.executeQuery("SELECT (SELECT count(*) FROM customers), (SELECT count(*) FROM app_users)")) {
            assertTrue(rows.next()); assertEquals(6, rows.getLong(1)); assertEquals(2, rows.getLong(2));
        }
    }

    private User account(String login, Role role) {
        User created = auth.create(admin, login, login, INITIAL, role);
        return auth.changePassword(auth.authenticate(created.login(), INITIAL), INITIAL, PERMANENT);
    }

    @Test void buyerOwnsOnlyTheirTicketsAndManagerConfirmsBeforePayment() throws Exception {
        permanentAdmin();
        User buyer = account("buyer1", Role.USER), other = account("buyer2", Role.USER), manager = account("operator", Role.MANAGER);
        assertNotNull(buyer.customerId()); assertNotEquals(buyer.customerId(), other.customerId());
        var personal = ApplicationServices.secured(config, auth, () -> buyer);
        var stranger = ApplicationServices.secured(config, auth, () -> other);
        var staff = ApplicationServices.secured(config, auth, () -> manager);
        var administration = ApplicationServices.secured(config, auth, () -> admin);
        var events = personal.events().findAll();
        assertEquals(1, personal.customers().findAll().size());
        assertTrue(personal.bookings().findAll().isEmpty());
        assertThrows(AuthException.class, () -> personal.customers().findById(other.customerId()));
        assertThrows(AuthException.class, () -> personal.bookings().createBooking(other.customerId(), events.getFirst().id()));
        var booking = personal.bookings().createBooking(buyer.customerId(), events.getFirst().id());
        assertEquals(1, personal.bookings().statistics().total());
        assertEquals(0, stranger.bookings().statistics().total());
        assertThrows(AuthException.class, () -> stranger.bookings().findById(booking.id()));
        assertThrows(AuthException.class, () -> stranger.bookings().findByTicketNumber(booking.ticketNumber()));
        assertTrue(stranger.bookings().searchByEventTitle(booking.event().title()).isEmpty());
        assertThrows(AuthException.class, () -> stranger.bookings().filterByCustomer(buyer.customerId()));
        assertTrue(stranger.bookings().sortByCreatedAt().isEmpty());
        assertTrue(stranger.bookings().sortByEventDate().isEmpty());
        assertThrows(AuthException.class, () -> stranger.bookings().delete(booking.id()));
        assertThrows(AuthException.class, () -> stranger.exporter().export(java.util.List.of(booking), Path.of("/tmp/forbidden-ticket-export.xlsx")));
        assertThrows(AuthException.class, () -> personal.bookings().changeStatus(booking.id(), ru.ticketpoint.model.BookingStatus.CONFIRMED));
        assertThrows(AuthException.class, () -> personal.bookings().changeStatus(booking.id(), ru.ticketpoint.model.BookingStatus.PAID));
        assertThrows(ru.ticketpoint.exception.BookingException.class,
                () -> staff.bookings().changeStatus(booking.id(), ru.ticketpoint.model.BookingStatus.PAID));
        var edited = personal.bookings().updateBooking(booking, buyer.customerId(), events.get(1).id(), ru.ticketpoint.model.BookingStatus.NEW);
        assertEquals(events.get(1).price(), edited.priceAtBooking());
        var confirmed = staff.bookings().changeStatus(booking.id(), ru.ticketpoint.model.BookingStatus.CONFIRMED);
        assertEquals(1, personal.bookings().statistics().confirmed());
        assertThrows(AuthException.class, () -> personal.bookings().updateBooking(confirmed, buyer.customerId(), events.getFirst().id(), ru.ticketpoint.model.BookingStatus.CONFIRMED));
        assertThrows(ru.ticketpoint.exception.BookingException.class, () -> personal.bookings().delete(booking.id()));
        staff.bookings().changeStatus(booking.id(), ru.ticketpoint.model.BookingStatus.PAID);
        assertThrows(AuthException.class, () -> personal.bookings().changeStatus(booking.id(), ru.ticketpoint.model.BookingStatus.CANCELLED));
        assertThrows(ru.ticketpoint.exception.BookingException.class, () -> staff.bookings().delete(booking.id()));
        staff.bookings().changeStatus(booking.id(), ru.ticketpoint.model.BookingStatus.CANCELLED);
        assertThrows(ru.ticketpoint.exception.BookingException.class, () -> personal.bookings().delete(booking.id()));
        administration.bookings().delete(booking.id());
        var draft = personal.bookings().createBooking(buyer.customerId(), events.getFirst().id());
        personal.bookings().delete(draft.id());
        var cancellable = personal.bookings().createBooking(buyer.customerId(), events.getFirst().id());
        staff.bookings().changeStatus(cancellable.id(), ru.ticketpoint.model.BookingStatus.CONFIRMED);
        assertEquals(ru.ticketpoint.model.BookingStatus.CANCELLED,
                personal.bookings().changeStatus(cancellable.id(), ru.ticketpoint.model.BookingStatus.CANCELLED).status());
    }

    @Test void administratorMaintainsCatalogAndAccountsWhileStaffCannot() {
        permanentAdmin();
        User manager = account("catalogstaff", Role.MANAGER);
        var staff = ApplicationServices.secured(config, auth, () -> manager);
        var administration = ApplicationServices.secured(config, auth, () -> admin);
        var customer = administration.customers().create(new ru.ticketpoint.model.CreateCustomerCommand("Опечатка"));
        assertEquals("Исправлено", administration.customers().update(customer.id(), new ru.ticketpoint.model.CreateCustomerCommand("Исправлено")).name());
        assertThrows(AuthException.class, () -> staff.customers().create(new ru.ticketpoint.model.CreateCustomerCommand("Запрещено")));
        assertThrows(AuthException.class, () -> staff.customers().delete(customer.id()));
        var command = new ru.ticketpoint.model.CreateEventCommand("Событие", "Зал", java.time.LocalDateTime.now().plusDays(60), new java.math.BigDecimal("100"));
        var event = administration.events().create(command);
        assertThrows(AuthException.class, () -> staff.events().update(event.id(), command));
        var updated = administration.events().update(event.id(), new ru.ticketpoint.model.CreateEventCommand("Исправлено", "Другой зал", command.startsAt(), new java.math.BigDecimal("200")));
        assertEquals("Другой зал", updated.venue());
        var booking = staff.bookings().createBooking(customer.id(), event.id());
        assertThrows(ru.ticketpoint.exception.BookingException.class, () -> administration.events().delete(event.id()));
        assertThrows(ru.ticketpoint.exception.BookingException.class, () -> administration.customers().delete(customer.id()));
        administration.bookings().delete(booking.id());
        administration.events().delete(event.id()); administration.customers().delete(customer.id());
        User buyer = account("reassign", Role.USER);
        User promoted = auth.update(admin, buyer, "Сотрудник", Role.MANAGER);
        assertThrows(AuthException.class, () -> auth.current(buyer));
        assertEquals(buyer.customerId(), promoted.customerId());
        User returned = auth.update(admin, promoted, "Покупатель", Role.USER);
        assertEquals(buyer.customerId(), returned.customerId());
        assertThrows(IllegalArgumentException.class, () -> auth.update(admin, buyer, "Устарело", Role.USER));
        assertThrows(IllegalArgumentException.class, () -> auth.update(admin, admin, "Admin", Role.USER));
    }

    @Test void migrationConvertsLegacyObserversWithoutAssigningExistingCustomersByName() throws Exception {
        permanentAdmin();
        try (var c = new JdbcConnectionProvider(config).open(); var s = c.createStatement()) {
            s.execute("ALTER TABLE app_users DROP CONSTRAINT app_users_role_check");
            s.execute("INSERT INTO app_users(login,name,password_hash,role) SELECT 'legacy','Анна Смирнова',password_hash,'VIEWER' FROM app_users WHERE login='admin'");
        }
        auth.initialize(new AuthConfig("admin", "Admin", null));
        User migrated = auth.list(admin).stream().filter(u -> u.login().equals("legacy")).findFirst().orElseThrow();
        assertEquals(Role.USER, migrated.role()); assertNotNull(migrated.customerId());
        assertEquals(2, migrated.version());
        auth.initialize(new AuthConfig("admin", "Admin", null));
        assertEquals(migrated.customerId(), auth.list(admin).stream().filter(u -> u.id() == migrated.id()).findFirst().orElseThrow().customerId());
        assertEquals(10, ApplicationServices.secured(config, auth, () -> admin).bookings().statistics().total());
    }

    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-12T10:00:00Z");
        @Override public ZoneId getZone() { return ZoneId.of("Europe/Moscow"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
