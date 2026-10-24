package ru.ticketpoint.backend;

import tools.jackson.databind.*;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import javax.sql.DataSource;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import ru.ticketpoint.auth.PasswordHasher;
import ru.ticketpoint.backend.domain.UserAccount;
import ru.ticketpoint.backend.repository.*;
import ru.ticketpoint.exception.BookingException;
import ru.ticketpoint.model.BookingStatus;
import ru.ticketpoint.repository.JdbcBookingRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "KR3_TEST_DB_URL", matches = ".+")
class BackendApiIntegrationTest {
    @BeforeAll
    static void jsonPathProvider() {
        com.jayway.jsonpath.Configuration.setDefaults(new com.jayway.jsonpath.Configuration.Defaults() {
            @Override
            public com.jayway.jsonpath.spi.json.JsonProvider jsonProvider() {
                return new com.jayway.jsonpath.spi.json.JacksonJsonProvider();
            }

            @Override
            public com.jayway.jsonpath.spi.mapper.MappingProvider mappingProvider() {
                return new com.jayway.jsonpath.spi.mapper.JacksonMappingProvider();
            }

            @Override
            public Set<com.jayway.jsonpath.Option> options() {
                return Set.of();
            }
        });
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("app.jwt.secret-base64", () -> "VGhpcy1pcy1hLXRlc3Qta2V5LW9ubHktMzItYnl0ZXM=");
        r.add("BOOTSTRAP_ADMIN_PASSWORD", () -> "Bootstrap-test-2026!");
        r.add("spring.datasource.url", () -> System.getenv("KR3_TEST_DB_URL"));
        r.add("spring.datasource.username", () -> System.getenv("KR3_TEST_DB_USER"));
        r.add("spring.datasource.password", () -> System.getenv("KR3_TEST_DB_PASSWORD"));
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    UserAccountRepository users;
    @Autowired
    DataSource dataSource;
    private String adminToken, viewerToken, managerToken;

    @Test
    void jdbcRespectsDisabledEventsAndArchivesInsteadOfDeleting() throws Exception {
        var event = createEvent();
        bookingInput(event.get("id").asString());
        long eventId;
        long customerId;
        try (var c = dataSource.getConnection(); var s = c.createStatement()) {
            try (var r = s.executeQuery("SELECT id FROM events WHERE public_id='" + event.get("id").asString() + "'")) {
                r.next();
                eventId = r.getLong(1);
            }
            try (var r = s.executeQuery("SELECT id FROM customers ORDER BY id LIMIT 1")) {
                r.next();
                customerId = r.getLong(1);
            }
        }
        var jdbc = new JdbcBookingRepository(dataSource::getConnection);
        var ticket = jdbc.create(customerId, eventId, "TP-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT));
        try (var c = dataSource.getConnection(); var s = c.prepareStatement("UPDATE bookings SET description='Changed through API' WHERE id=?")) {
            s.setLong(1, ticket.id());
            s.executeUpdate();
        }
        var stale = ticket;
        assertThrows(BookingException.class,
                () -> jdbc.update(stale, stale.customer().id(), stale.event().id(), BookingStatus.NEW));
        assertFalse(jdbc.delete(stale));
        ticket = jdbc.findById(ticket.id()).orElseThrow();
        long archivedId = ticket.id();
        assertTrue(jdbc.delete(ticket));
        try (var c = dataSource.getConnection(); var s = c.prepareStatement("SELECT archived FROM bookings WHERE id=?")) {
            s.setLong(1, ticket.id());
            try (var r = s.executeQuery()) {
                assertTrue(r.next(), "JDBC must preserve the booking");
                assertTrue(r.getBoolean(1));
            }
        }
        assertTrue(jdbc.findAll().stream().noneMatch(b -> b.id().equals(archivedId)));
        assertFalse(jdbc.delete(ticket), "An archived booking cannot be deleted through JDBC");
        try (var c = dataSource.getConnection(); var s = c.prepareStatement("UPDATE events SET active=false WHERE id=?")) {
            s.setLong(1, eventId);
            s.executeUpdate();
        }
        assertThrows(BookingException.class,
                () -> jdbc.create(customerId, eventId, "TP-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT)));
    }

    @Test
    void buyerCanBookOwnTicketButCannotBookForAnotherCustomer() throws Exception {
        var user = users.findAll().stream().filter(u -> u.getRole().equals("USER") && u.getCustomerId() == null).findFirst().orElseThrow();

        mvc.perform(put("/api/admin/users/" + user.getPublicId())
                .header("Authorization", "Bearer " + adminToken).contentType("application/json")
                .content(json.writeValueAsString(Map.of("login", user.getLogin(), "name", user.getName(),
                        "role", "USER", "enabled", true, "version", user.getVersion())))).andExpect(status().isOk());
        String token = login(user.getLogin(), "Integration-password-2026!").get("accessToken").asString();
        user = users.findByPublicId(user.getPublicId()).orElseThrow();
        var event = createEvent();
        var input = bookingInput(event.get("id").asString());
        input.put("customerId", user.getCustomerId());
        input.put("vip", false);
        input.put("tags", List.of());
        var own = data(mvc.perform(post("/api/bookings").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content(json.writeValueAsString(input)))
                .andExpect(status().isCreated()).andReturn());
        mvc.perform(get("/api/bookings/" + own.get("id").asString()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        input.put("customerId", user.getCustomerId() + 100000);
        mvc.perform(post("/api/bookings").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content(json.writeValueAsString(input)))
                .andExpect(status().isForbidden());
        input.put("customerId", user.getCustomerId());
        input.put("status", "PAID");
        input.put("version", own.get("version").asLong());
        mvc.perform(put("/api/bookings/" + own.get("id").asString()).header("Authorization", "Bearer " + token)
                        .contentType("application/json").content(json.writeValueAsString(input)))
                .andExpect(status().isForbidden());
    }

    @Test
    void registrationCreatesPermanentBuyerAndOwnBookingWorkflow() throws Exception {
        String login = "buyer-" + UUID.randomUUID().toString().substring(0, 12);
        var signup = Map.of("login", login, "name", "Покупатель", "password", "Buyer-password-2026!", "confirmation", "Buyer-password-2026!", "role", "ADMIN");
        var tokens = data(mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content(json.writeValueAsString(signup))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.passwordChangeRequired").value(false)).andReturn());
        String token = tokens.get("accessToken").asString();
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content(json.writeValueAsString(signup))).andExpect(status().isConflict());
        var ownCustomers = data(mvc.perform(get("/api/customers").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn());
        assertEquals(1, ownCustomers.size());
        var event = createEvent();
        assertTrue(event.get("available").asBoolean());
        var body = new LinkedHashMap<>(Map.of("customerId", ownCustomers.get(0).get("id").asLong(),
                "eventId", event.get("id").asString(), "status", "NEW", "vip", false, "tags", List.of()));
        var booking = data(mvc.perform(post("/api/bookings").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn());
        assertTrue(Duration.between(LocalDateTime.parse(booking.get("createdAt").asString()),
                LocalDateTime.now(ZoneId.of("Europe/Moscow"))).abs().getSeconds() < 10);
        body.put("version", booking.get("version").asLong());
        body.put("description", "Мой комментарий");
        var edited = data(mvc.perform(put("/api/bookings/" + booking.get("id").asString())
                .header("Authorization", "Bearer " + token).contentType("application/json")
                .content(json.writeValueAsString(body))).andExpect(status().isOk()).andReturn());
        assertTrue(Duration.between(LocalDateTime.parse(edited.get("updatedAt").asString()),
                LocalDateTime.now(ZoneId.of("Europe/Moscow"))).abs().getSeconds() < 10);
        mvc.perform(delete("/api/bookings/" + booking.get("id").asString()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(post("/api/bookings/" + booking.get("id").asString() + "/restore").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/bookings").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/bookings?archived=true").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @BeforeEach
    void actors() throws Exception {
        adminToken = actor("ADMIN", false);
        viewerToken = actor("USER", false);
        managerToken = actor("MANAGER", false);
    }

    private String actor(String role, boolean temporary) throws Exception {
        var u = new UserAccount();
        u.setLogin("test-" + UUID.randomUUID().toString().substring(0, 16));
        u.setName("Test " + role);
        u.setRole(role);
        u.setPasswordChangeRequired(temporary);
        u.setPasswordHash(new PasswordHasher().hash("Integration-password-2026!"));
        users.saveAndFlush(u);
        return login(u.getLogin(), "Integration-password-2026!").get("accessToken").asString();
    }

    private JsonNode login(String login, String password) throws Exception {
        return data(
                mvc.perform(
                                post("/api/auth/login")
                                        .contentType("application/json")
                                        .content(json.writeValueAsString(Map.of("login", login, "password", password))))
                        .andExpect(status().isOk())
                        .andReturn());
    }

    private JsonNode data(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private Map<String, Object> eventInput() {
        return Map.of(
                "title",
                "Test " + UUID.randomUUID(),
                "venue",
                "Test venue",
                "startsAt",
                LocalDateTime.now().plusDays(90).toString(),
                "price",
                6500,
                "active",
                true);
    }

    private JsonNode createEvent() throws Exception {
        return data(
                mvc.perform(
                                post("/api/events")
                                        .header("Authorization", "Bearer " + adminToken)
                                        .contentType("application/json")
                                        .content(json.writeValueAsString(eventInput())))
                        .andExpect(status().isCreated())
                        .andReturn());
    }

    private Map<String, Object> bookingInput(String event) throws Exception {
        var customer = data(mvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("name", "Алексей Волков"))))
                .andExpect(status().isCreated()).andReturn());
        return new LinkedHashMap<>(
                Map.of(
                        "eventId",
                        event,
                        "customerId",
                        customer.get("id").asLong(),
                        "status",
                        "NEW",
                        "vip",
                        true,
                        "description",
                        "Integration проверка",
                        "tags",
                        List.of("test", "vip")));
    }

    @Test
    void observerCanReadButCannotChangeBookings() throws Exception {
        String observerToken = actor("VIEWER", false);
        var event = createEvent();
        var input = bookingInput(event.get("id").asString());
        var booking = data(mvc.perform(post("/api/bookings")
                .header("Authorization", "Bearer " + managerToken).contentType("application/json")
                .content(json.writeValueAsString(input))).andExpect(status().isCreated()).andReturn());
        String id = booking.get("id").asString();
        mvc.perform(get("/api/bookings/" + id).header("Authorization", "Bearer " + observerToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/events").header("Authorization", "Bearer " + observerToken))
                .andExpect(status().isOk());
        mvc.perform(post("/api/bookings").header("Authorization", "Bearer " + observerToken)
                        .contentType("application/json").content(json.writeValueAsString(input)))
                .andExpect(status().isForbidden());
        input.put("version", booking.get("version").asLong());
        mvc.perform(put("/api/bookings/" + id).header("Authorization", "Bearer " + observerToken)
                        .contentType("application/json").content(json.writeValueAsString(input)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/bookings/" + id).header("Authorization", "Bearer " + observerToken))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/bookings/bulk").header("Authorization", "Bearer " + observerToken)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("ids", List.of(id), "action", "ARCHIVE"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/bookings/" + id + "/notes")
                        .header("Authorization", "Bearer " + observerToken).contentType("application/json")
                        .content("{\"body\":\"Заметка\",\"pinned\":false}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + observerToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/bookings/" + id).header("Authorization", "Bearer " + observerToken))
                .andExpect(jsonPath("$.version").value(booking.get("version").asLong()))
                .andExpect(jsonPath("$.archived").value(false));
    }

    @Test
    void expiredEventCannotBeConfirmedByUpdateBulkOrCsv() throws Exception {
        var event = createEvent();
        String eventId = event.get("id").asString();
        var input = bookingInput(eventId);
        var booking = data(mvc.perform(post("/api/bookings")
                .header("Authorization", "Bearer " + managerToken).contentType("application/json")
                .content(json.writeValueAsString(input))).andExpect(status().isCreated()).andReturn());
        String id = booking.get("id").asString();
        String csv = "ticketNumber,customerId,eventId,status,vip,description,tags\n"
                + booking.get("ticketNumber").asString() + "," + input.get("customerId") + "," + eventId
                + ",CONFIRMED,true,CSV,test;vip\n";
        var file = new MockMultipartFile("file", "bookings.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        var futurePreview = data(mvc.perform(multipart("/api/bookings/import/preview").file(file)
                        .param("mode", "UPSERT").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.validRows").value(1)).andReturn());
        var changedEvent = new LinkedHashMap<>(eventInput());
        changedEvent.put("startsAt", LocalDateTime.now().minusDays(1).toString());
        changedEvent.put("version", event.get("version").asLong());
        mvc.perform(put("/api/events/" + eventId).header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(json.writeValueAsString(changedEvent)))
                .andExpect(status().isOk());
        input.put("version", booking.get("version").asLong());
        input.put("status", "CONFIRMED");
        mvc.perform(put("/api/bookings/" + id).header("Authorization", "Bearer " + managerToken)
                        .contentType("application/json").content(json.writeValueAsString(input)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/bookings/bulk").header("Authorization", "Bearer " + managerToken)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("ids", List.of(id), "action", "SET_STATUS", "status", "CONFIRMED"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.succeeded").value(0))
                .andExpect(jsonPath("$.failures.length()").value(1));
        mvc.perform(post("/api/bookings/import/apply").header("Authorization", "Bearer " + managerToken)
                        .contentType("application/json").content(json.writeValueAsString(Map.of(
                                "previewId", futurePreview.get("previewId").asString(), "mode", "UPSERT"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.succeeded").value(0))
                .andExpect(jsonPath("$.errors.length()").value(1));
        mvc.perform(multipart("/api/bookings/import/preview").file(file).param("mode", "UPSERT")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.validRows").value(0))
                .andExpect(jsonPath("$.invalidRows").value(1));
        mvc.perform(get("/api/bookings/" + id).header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.version").value(booking.get("version").asLong()));
    }

    @Test
    void guestCanSearchAndReadButCannotMutateOrReadPrivateEndpoints() throws Exception {
        var event = createEvent();
        var input = bookingInput(event.get("id").asString());
        var created = data(mvc.perform(post("/api/bookings")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json").content(json.writeValueAsString(input)))
                .andExpect(status().isCreated()).andReturn());
        String id = created.get("id").asString();
        mvc.perform(get("/api/bookings").param("q", created.get("ticketNumber").asString())
                .param("status", "NEW").param("sort", "price").param("direction", "desc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/bookings/" + id)).andExpect(status().isOk());
        mvc.perform(get("/api/events")).andExpect(status().isOk());
        mvc.perform(get("/api/analytics/summary")).andExpect(status().isOk());
        mvc.perform(post("/api/bookings").contentType("application/json")
                .content(json.writeValueAsString(input))).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/bookings/" + id).contentType("application/json")
                .content(json.writeValueAsString(input))).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/bookings/" + id)).andExpect(status().isUnauthorized());
        for (String path : List.of("/api/customers", "/api/admin/users", "/api/bookings/" + id + "/notes"))
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
    }

    @Test
    void contextHealthMigrationsAndRoleBoundaries() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/api/bookings"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-ID"));
        mvc.perform(
                        get("/api/bookings")
                                .header("Authorization", "Bearer " + viewerToken)
                                .header("X-Correlation-ID", "test-request-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", "test-request-1"));
        mvc.perform(
                        post("/api/events")
                                .header("Authorization", "Bearer " + managerToken)
                                .contentType("application/json")
                                .content(json.writeValueAsString(eventInput())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/v3/api-docs").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/bookings']").exists());
        mvc.perform(get("/api/bookings?size=101").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bookingLifecycleFiltersNotesBulkAndAudit() throws Exception {
        String event = createEvent().get("id").asString();
        var input = bookingInput(event);
        mvc.perform(
                        post("/api/bookings")
                                .header("Authorization", "Bearer " + viewerToken)
                                .contentType("application/json")
                                .content(json.writeValueAsString(input)))
                .andExpect(status().isForbidden());
        var booking =
                data(
                        mvc.perform(
                                        post("/api/bookings")
                                                .header("Authorization", "Bearer " + managerToken)
                                                .contentType("application/json")
                                                .content(json.writeValueAsString(input)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.priority").value("HIGH"))
                                .andReturn());
        String id = booking.get("id").asString();
        long version = booking.get("version").asLong();
        input.put("version", version);
        input.put("status", "CONFIRMED");
        mvc.perform(
                        put("/api/bookings/" + id)
                                .header("Authorization", "Bearer " + managerToken)
                                .contentType("application/json")
                                .content(json.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(version + 1));
        mvc.perform(
                        put("/api/bookings/" + id)
                                .header("Authorization", "Bearer " + managerToken)
                                .contentType("application/json")
                                .content(json.writeValueAsString(input)))
                .andExpect(status().isConflict());
        mvc.perform(
                        get("/api/bookings")
                                .param("eventId", event)
                                .param("status", "CONFIRMED")
                                .param("priority", "HIGH")
                                .param("vip", "true")
                                .param("tag", "test")
                                .param("price", "6000")
                                .param("comparison", "gte")
                                .param("q", "Integration")
                                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(
                        get("/api/bookings")
                                .param("q", "%' OR 1=1 --")
                                .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        var note =
                data(
                        mvc.perform(
                                        post("/api/bookings/" + id + "/notes")
                                                .header("Authorization", "Bearer " + managerToken)
                                                .contentType("application/json")
                                                .content("{\"body\":\"Проверить оплату\",\"pinned\":true}"))
                                .andExpect(status().isCreated())
                                .andReturn());
        String noteId = note.get("id").asString();
        mvc.perform(
                        put("/api/bookings/" + id + "/notes/" + noteId)
                                .header("Authorization", "Bearer " + managerToken)
                                .contentType("application/json")
                                .content("{\"body\":\"Оплата проверена\",\"pinned\":false,\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(
                        get("/api/bookings/" + id + "/notes").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].authorName").exists());
        String missing = UUID.randomUUID().toString();
        mvc.perform(
                        post("/api/bookings/bulk")
                                .header("Authorization", "Bearer " + managerToken)
                                .contentType("application/json")
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "ids", List.of(id, missing), "action", "ADD_TAG", "tag", "priority"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.succeeded").value(1))
                .andExpect(jsonPath("$.failures[0].id").value(missing));
        mvc.perform(delete("/api/bookings/" + id).header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));
        mvc.perform(
                        post("/api/bookings/" + id + "/restore")
                                .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(false));
        mvc.perform(
                        delete("/api/bookings/" + id + "/permanent")
                                .header("Authorization", "Bearer " + managerToken)
                                .header("X-Confirm-Delete", "PERMANENTLY_DELETE"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/bookings/" + id).header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk());
        mvc.perform(
                        delete("/api/bookings/" + id + "/permanent")
                                .header("Authorization", "Bearer " + adminToken)
                                .header("X-Confirm-Delete", "PERMANENTLY_DELETE"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/bookings/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
        mvc.perform(
                        get("/api/admin/audit?q=BOOKING_CREATED")
                                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].correlationId").isString());
        mvc.perform(get("/api/analytics/summary").header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byPriority").exists());
    }

    @Test
    void csvPreviewApplyOwnershipReplayAndUpsert() throws Exception {
        String event = createEvent().get("id").asString();
        var input = bookingInput(event);
        String code = "CSV-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT);
        String text =
                "ticketNumber,customerId,eventId,status,vip,description,tags\n"
                        + code
                        + ","
                        + input.get("customerId")
                        + ","
                        + event
                        + ",NEW,true,CSV test,test;import\nBAD,not-a-number,invalid,NEW,false,,\n";
        var file =
                new MockMultipartFile(
                        "file", "bookings.csv", "text/csv", text.getBytes(StandardCharsets.UTF_8));
        var preview =
                data(
                        mvc.perform(
                                        multipart("/api/bookings/import/preview")
                                                .file(file)
                                                .param("mode", "NEW_ONLY")
                                                .header("Authorization", "Bearer " + managerToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.validRows").value(1))
                                .andExpect(jsonPath("$.invalidRows").value(1))
                                .andReturn());
        String body =
                json.writeValueAsString(
                        Map.of("previewId", preview.get("previewId").asString(), "mode", "NEW_ONLY"));
        mvc.perform(
                        post("/api/bookings/import/apply")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post("/api/bookings/import/apply")
                                .header("Authorization", "Bearer " + managerToken)
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.succeeded").value(1));
        mvc.perform(
                        post("/api/bookings/import/apply")
                                .header("Authorization", "Bearer " + managerToken)
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isBadRequest());
        var exported =
                mvc.perform(
                                get("/api/bookings/export?q=" + code)
                                        .header("Authorization", "Bearer " + managerToken))
                        .andExpect(status().isOk())
                        .andExpect(content().contentTypeCompatibleWith("text/csv"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8);
        assertTrue(exported.contains(code));
        assertFalse(exported.contains("BAD,"));
        var upsert =
                data(
                        mvc.perform(
                                        multipart("/api/bookings/import/preview")
                                                .file(
                                                        new MockMultipartFile(
                                                                "file",
                                                                "bookings.csv",
                                                                "text/csv",
                                                                exported
                                                                        .replace("CSV test", "Updated CSV")
                                                                        .getBytes(StandardCharsets.UTF_8)))
                                                .param("mode", "UPSERT")
                                                .header("Authorization", "Bearer " + managerToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.validRows").value(1))
                                .andReturn());
        mvc.perform(
                        post("/api/bookings/import/apply")
                                .header("Authorization", "Bearer " + managerToken)
                                .contentType("application/json")
                                .content(
                                        json.writeValueAsString(
                                                Map.of("previewId", upsert.get("previewId").asString(), "mode", "UPSERT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.succeeded").value(1));
    }

    @Test
    void temporaryPasswordRefreshRotationLogoutLockoutAndAdminRevocation() throws Exception {
        String temporary = actor("MANAGER", true);
        mvc.perform(get("/api/bookings").header("Authorization", "Bearer " + temporary))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/api/auth/password")
                                .header("Authorization", "Bearer " + temporary)
                                .contentType("application/json")
                                .content(
                                        "{\"currentPassword\":\"Integration-password-2026!\",\"newPassword\":\"Changed-password-2026!\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + temporary))
                .andExpect(status().isUnauthorized());
        String login = "refresh-" + UUID.randomUUID().toString().substring(0, 12);
        var u = new UserAccount();
        u.setLogin(login);
        u.setName("Refresh");
        u.setRole("USER");
        u.setPasswordChangeRequired(false);
        u.setPasswordHash(new PasswordHasher().hash("Refresh-password-2026!"));
        users.saveAndFlush(u);
        var first = login(login, "Refresh-password-2026!");
        var second =
                data(
                        mvc.perform(
                                        post("/api/auth/refresh")
                                                .header("Authorization", "Bearer expired-access-token")
                                                .contentType("application/json")
                                                .content(
                                                        json.writeValueAsString(
                                                                Map.of("refreshToken", first.get("refreshToken").asString()))))
                                .andExpect(status().isOk())
                                .andReturn());
        mvc.perform(
                        post("/api/auth/refresh")
                                .contentType("application/json")
                                .content(
                                        json.writeValueAsString(
                                                Map.of("refreshToken", first.get("refreshToken").asString()))))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/api/auth/refresh")
                                .contentType("application/json")
                                .content(
                                        json.writeValueAsString(
                                                Map.of("refreshToken", second.get("refreshToken").asString()))))
                .andExpect(status().isUnauthorized());
        var third = login(login, "Refresh-password-2026!");
        mvc.perform(
                        post("/api/auth/logout")
                                .header("Authorization", "Bearer " + third.get("accessToken").asString())
                                .contentType("application/json")
                                .content(
                                        json.writeValueAsString(
                                                Map.of("refreshToken", third.get("refreshToken").asString()))))
                .andExpect(status().isNoContent());
        mvc.perform(
                        post("/api/auth/refresh")
                                .contentType("application/json")
                                .content(
                                        json.writeValueAsString(
                                                Map.of("refreshToken", third.get("refreshToken").asString()))))
                .andExpect(status().isUnauthorized());
        for (int i = 0; i < 5; i++)
            mvc.perform(
                            post("/api/auth/login")
                                    .contentType("application/json")
                                    .content(
                                            json.writeValueAsString(
                                                    Map.of("login", login, "password", "Wrong-password-2026!"))))
                    .andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/api/auth/login")
                                .contentType("application/json")
                                .content(
                                        json.writeValueAsString(
                                                Map.of("login", login, "password", "Refresh-password-2026!"))))
                .andExpect(status().isUnauthorized());
        assertNotNull(users.findByPublicId(u.getPublicId()).orElseThrow().getLockedUntil());
    }

    @Test
    void adminAccountsAndCustomerIsolation() throws Exception {
        String login = "customer-" + UUID.randomUUID().toString().substring(0, 12);
        var created =
                data(
                        mvc.perform(
                                        post("/api/admin/users")
                                                .header("Authorization", "Bearer " + adminToken)
                                                .contentType("application/json")
                                                .content(
                                                        json.writeValueAsString(
                                                                Map.of(
                                                                        "user",
                                                                        Map.of(
                                                                                "login",
                                                                                login,
                                                                                "name",
                                                                                "Customer test",
                                                                                "role",
                                                                                "USER",
                                                                                "enabled",
                                                                                true),
                                                                        "temporaryPassword",
                                                                        "Customer-password-2026!"))))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                                .andReturn());
        String id = created.get("id").asString();
        var tokens = login(login, "Customer-password-2026!");
        String token = tokens.get("accessToken").asString();
        mvc.perform(
                        post("/api/auth/password")
                                .header("Authorization", "Bearer " + token)
                                .contentType("application/json")
                                .content(
                                        "{\"currentPassword\":\"Customer-password-2026!\",\"newPassword\":\"Customer-permanent-2026!\"}"))
                .andExpect(status().isNoContent());
        token = login(login, "Customer-permanent-2026!").get("accessToken").asString();
        mvc.perform(get("/api/bookings").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        var foreign =
                data(
                        mvc.perform(
                                        post("/api/bookings")
                                                .header("Authorization", "Bearer " + managerToken)
                                                .contentType("application/json")
                                                .content(
                                                        json.writeValueAsString(
                                                                bookingInput(createEvent().get("id").asString()))))
                                .andExpect(status().isCreated())
                                .andReturn());
        mvc.perform(
                        get("/api/bookings/" + foreign.get("id").asString())
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(
                        get("/api/bookings/" + foreign.get("id").asString() + "/notes")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        var u = users.findByPublicId(UUID.fromString(id)).orElseThrow();
        assertNotNull(u.getCustomerId());
        var edit =
                new LinkedHashMap<>(
                        Map.of(
                                "login",
                                login + "-new",
                                "name",
                                "Changed user",
                                "role",
                                "USER",
                                "enabled",
                                false,
                                "version",
                                u.getVersion()));
        mvc.perform(
                        put("/api/admin/users/" + id)
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType("application/json")
                                .content(json.writeValueAsString(edit)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/api/auth/login")
                                .contentType("application/json")
                                .content(
                                        json.writeValueAsString(
                                                Map.of("login", login + "-new", "password", "Customer-permanent-2026!"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/api/admin/users/" + id + "/reset-password")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType("application/json")
                                .content("{\"temporaryPassword\":\"Reset-password-2026!\"}"))
                .andExpect(status().isNoContent());
        u = users.findByPublicId(UUID.fromString(id)).orElseThrow();
        assertTrue(u.getPasswordChangeRequired());
        assertTrue(new PasswordHasher().matches("Reset-password-2026!", u.getPasswordHash()));
    }
}
