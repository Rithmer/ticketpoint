package ru.ticketpoint.backend;

import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.ticketpoint.auth.*;
import ru.ticketpoint.config.ApplicationServices;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiSecurityTest {
    private AuthService auth;
    private ru.ticketpoint.service.BookingService bookings;
    private MockMvc mvc;
    private final User admin = new User(1, "admin", "Администратор", Role.ADMIN, true, false, 1);
    @BeforeEach void setup() {
        auth = mock(AuthService.class);
        ApplicationServices services = mock(ApplicationServices.class);
        bookings = mock(ru.ticketpoint.service.BookingService.class);
        when(services.bookings()).thenReturn(bookings);
        when(bookings.findAll()).thenReturn(List.of());
        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (user == null) throw new AuthException("Войдите в приложение");
            return user;
        }).when(auth).current(any());
        mvc = MockMvcBuilders.standaloneSetup(new TicketPointController(services), new AuthController(auth))
                .addInterceptors(new ApiSecurity(auth)).setControllerAdvice(new ApiErrors()).build();
    }
    private MockHttpSession session(User user) {
        var session = new MockHttpSession(); session.setAttribute(AuthController.USER, user);
        session.setAttribute(AuthController.CSRF, "test-csrf-token"); return session;
    }
    @Test void privateDataRequiresLoginAndMutationsRequireCsrf() throws Exception {
        mvc.perform(get("/api/bookings")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/bookings/1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/bookings").session(session(admin))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(auth).requireRead(admin);
        mvc.perform(delete("/api/bookings/1").session(session(admin))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/bookings/1").session(session(admin)).header("X-CSRF-Token", "wrong"))
                .andExpect(status().isForbidden());
    }
    @Test void checksWriteAndAdminRolesEvenForDirectApiCalls() throws Exception {
        User viewer = new User(2, "viewer", "Viewer", Role.USER, true, false, 1);
        doThrow(new AuthException("Чужое бронирование", true)).when(bookings).delete(1L);
        doThrow(new AuthException("Недостаточно прав", true)).when(auth).requireAdmin(viewer);
        mvc.perform(delete("/api/bookings/1").session(session(viewer)).header("X-CSRF-Token", "test-csrf-token"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/users").session(session(viewer))).andExpect(status().isForbidden());
        verify(auth).requireRead(viewer); verify(auth).requireAdmin(viewer);
    }
    @Test void publicRegistrationRequiresCsrfAndCannotSelectPrivilegedRole() throws Exception {
        User buyer = new User(9, "buyer", "Покупатель", Role.USER, true, false, 1, 12L);
        when(auth.register("buyer", "Покупатель", "Buyer-password-2026!", "Buyer-password-2026!")).thenReturn(buyer);
        String body = """
                {"login":"buyer","name":"Покупатель","password":"Buyer-password-2026!",
                 "confirmation":"Buyer-password-2026!","role":"ADMIN","customerId":1,"enabled":true}
                """;
        MockHttpSession session = session(null); String previousId = session.getId();
        mvc.perform(post("/api/auth/register").session(session).contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        verify(auth, never()).register(anyString(), anyString(), anyString(), anyString());
        mvc.perform(post("/api/auth/register").session(session).header("X-CSRF-Token", "test-csrf-token")
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.customerId").value(12))
                .andExpect(jsonPath("$.user.passwordChangeRequired").value(false));
        Assertions.assertNotEquals(previousId, session.getId());
        Assertions.assertEquals(buyer, session.getAttribute(AuthController.USER));
        Assertions.assertNotEquals("test-csrf-token", session.getAttribute(AuthController.CSRF));
        verify(auth).register("buyer", "Покупатель", "Buyer-password-2026!", "Buyer-password-2026!");
    }

    @Test void loginRotatesSessionAndTokenAndLogoutInvalidatesSession() throws Exception {
        MockHttpSession session = session(null); String id = session.getId();
        when(auth.authenticate("admin", "secret-password")).thenReturn(admin);
        String response = mvc.perform(post("/api/auth/login").session(session).header("X-CSRF-Token", "test-csrf-token")
                        .contentType("application/json").content("{\"login\":\"admin\",\"password\":\"secret-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.login").value("admin"))
                .andExpect(jsonPath("$.csrfToken").isString()).andReturn().getResponse().getContentAsString();
        Assertions.assertNotEquals(id, session.getId());
        Assertions.assertFalse(response.contains("secret-password"));
        Assertions.assertNotEquals("test-csrf-token", session.getAttribute(AuthController.CSRF));
        mvc.perform(post("/api/auth/logout").session(session).header("X-CSRF-Token", session.getAttribute(AuthController.CSRF)))
                .andExpect(status().isNoContent());
        Assertions.assertTrue(session.isInvalid());
    }
    @Test void temporaryPasswordBlocksDataButAllowsPasswordChange() throws Exception {
        User temporary = new User(3, "newuser", "New", Role.MANAGER, true, true, 1);
        doThrow(new AuthException("Смените временный пароль", true)).when(auth).requireRead(temporary);
        mvc.perform(get("/api/bookings").session(session(temporary))).andExpect(status().isForbidden());
        when(auth.changePassword(temporary, "initial-password", "permanent-password")).thenReturn(admin);
        mvc.perform(post("/api/auth/password").session(session(temporary)).header("X-CSRF-Token", "test-csrf-token")
                        .contentType("application/json").content("{\"currentPassword\":\"initial-password\",\"newPassword\":\"permanent-password\"}"))
                .andExpect(status().isOk());
        verify(auth).changePassword(temporary, "initial-password", "permanent-password");
    }
}
