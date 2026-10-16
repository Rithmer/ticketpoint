package ru.ticketpoint.backend;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.ticketpoint.config.ApplicationServices;
import ru.ticketpoint.model.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public final class TicketPointController {
    private final ApplicationServices services;
    public TicketPointController(ApplicationServices services) { this.services = services; }

    @GetMapping("/bookings") public List<Booking> bookings() { return services.bookings().findAll(); }
    @GetMapping("/bookings/{id}") public Booking booking(@PathVariable long id) { return services.bookings().findById(id); }
    @PostMapping("/bookings") @ResponseStatus(HttpStatus.CREATED)
    public Booking createBooking(@RequestBody NewBooking request) {
        return services.bookings().createBooking(request.customerId(), request.eventId());
    }
    @PatchMapping("/bookings/{id}/status") public Booking changeStatus(@PathVariable long id, @RequestBody NewStatus request) {
        return services.bookings().changeStatus(id, request.status());
    }
    @DeleteMapping("/bookings/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBooking(@PathVariable long id) { services.bookings().delete(id); }
    @PutMapping("/bookings/{id}") public Booking updateBooking(@PathVariable long id, @RequestBody EditBooking request) {
        if (request.expected() == null || !Long.valueOf(id).equals(request.expected().id()))
            throw new IllegalArgumentException("Выбрано другое бронирование. Обновите список");
        return services.bookings().updateBooking(request.expected(), request.customerId(), request.eventId(), request.status());
    }
    @GetMapping("/customers") public List<Customer> customers() { return services.customers().findAll(); }
    @PostMapping("/customers") @ResponseStatus(HttpStatus.CREATED)
    public Customer createCustomer(@RequestBody CreateCustomerCommand command) { return services.customers().create(command); }
    @PutMapping("/customers/{id}") public Customer updateCustomer(@PathVariable long id, @RequestBody CreateCustomerCommand command) {
        return services.customers().update(id, command);
    }
    @DeleteMapping("/customers/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCustomer(@PathVariable long id) { services.customers().delete(id); }
    @GetMapping("/events") public List<Event> events() { return services.events().findAll(); }
    @PostMapping("/events") @ResponseStatus(HttpStatus.CREATED)
    public Event createEvent(@RequestBody CreateEventCommand command) { return services.events().create(command); }
    @PutMapping("/events/{id}") public Event updateEvent(@PathVariable long id, @RequestBody CreateEventCommand command) {
        return services.events().update(id, command);
    }
    @DeleteMapping("/events/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEvent(@PathVariable long id) { services.events().delete(id); }
    @GetMapping("/statistics") public BookingStatistics statistics() { return services.bookings().statistics(); }

    public record NewBooking(long customerId, long eventId) { }
    public record EditBooking(Booking expected, long customerId, long eventId, BookingStatus status) { }
    public record NewStatus(BookingStatus status) { }
}
