package ru.ticketpoint.backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.ticketpoint.config.ApplicationServices;
import ru.ticketpoint.exception.DataAccessException;
import ru.ticketpoint.exception.EntityNotFoundException;
import ru.ticketpoint.model.*;
import ru.ticketpoint.service.BookingService;
import ru.ticketpoint.service.CustomerService;
import ru.ticketpoint.service.EventService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TicketPointControllerTest {
    private BookingService bookings;
    private CustomerService customers;
    private MockMvc mvc;
    private Booking booking;

    @BeforeEach void setup() {
        bookings = mock(BookingService.class);
        customers = mock(CustomerService.class);
        ApplicationServices services = mock(ApplicationServices.class);
        when(services.bookings()).thenReturn(bookings);
        when(services.customers()).thenReturn(customers);
        when(services.events()).thenReturn(mock(EventService.class));
        mvc = MockMvcBuilders.standaloneSetup(new TicketPointController(services)).setControllerAdvice(new ApiErrors()).build();
        Customer customer = new Customer(1L, "CUS-74B5C567-21F3-4CC8-AE4C-9968CAD65A40", "Иван");
        Event event = new Event(2L, "Концерт", "Зал", LocalDateTime.of(2030, 1, 1, 19, 0), new BigDecimal("1500.00"));
        booking = new Booking(3L, "TP-74B5C567-21F3-4CC8-AE4C-9968CAD65A40", customer, event,
                event.price(), BookingStatus.NEW, LocalDateTime.of(2026, 10, 12, 10, 0));
    }

    @Test void createsAndListsBookingUsingSharedServices() throws Exception {
        when(bookings.createBooking(1, 2)).thenReturn(booking);
        mvc.perform(post("/api/bookings").contentType("application/json").content("{\"customerId\":1,\"eventId\":2}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.customer.name").value("Иван"));
        verify(bookings).createBooking(1, 2);
        when(bookings.findAll()).thenReturn(List.of(booking));
        mvc.perform(get("/api/bookings")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].event.title").value("Концерт"));
    }

    @Test void editsAndDeletesBooking() throws Exception {
        when(bookings.changeStatus(3, BookingStatus.PAID)).thenReturn(booking);
        mvc.perform(patch("/api/bookings/3/status").contentType("application/json").content("{\"status\":\"PAID\"}"))
                .andExpect(status().isOk());
        verify(bookings).changeStatus(3, BookingStatus.PAID);
        mvc.perform(delete("/api/bookings/3")).andExpect(status().isNoContent());
        verify(bookings).delete(3);
    }

    @Test void reportsUnknownBookingAndInvalidJsonWithoutInternalDetails() throws Exception {
        when(bookings.findById(99)).thenThrow(new EntityNotFoundException("Бронирование не найдено"));
        mvc.perform(get("/api/bookings/99")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Бронирование не найдено"));
        mvc.perform(patch("/api/bookings/3/status").contentType("application/json").content("{\"status\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        verify(bookings, never()).changeStatus(anyLong(), any());
        mvc.perform(post("/api/customers").contentType("application/json").content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest());
        verify(customers, never()).create(any());
    }

    @Test void databaseFailureReturnsServiceUnavailable() throws Exception {
        when(bookings.findAll()).thenThrow(new DataAccessException("private SQL", new java.sql.SQLException("private SQL")));
        mvc.perform(get("/api/bookings")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("База данных недоступна. Проверьте подключение и повторите действие."));
    }
}
