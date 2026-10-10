package ru.ticketpoint.config;

import ru.ticketpoint.export.BookingExporter;
import ru.ticketpoint.export.ExcelBookingExporter;
import ru.ticketpoint.repository.*;
import ru.ticketpoint.service.*;

import java.time.Clock;
import java.util.Objects;

public record ApplicationServices(BookingService bookings, CustomerService customers, EventService events,
                                  DatabaseMetadataService metadata, DatabasePreflight preflight,
                                  BookingExporter exporter, Clock clock) {
    public ApplicationServices {
        Objects.requireNonNull(bookings);
        Objects.requireNonNull(customers);
        Objects.requireNonNull(events);
        Objects.requireNonNull(metadata);
        Objects.requireNonNull(preflight);
        Objects.requireNonNull(exporter);
        Objects.requireNonNull(clock);
    }

    public static ApplicationServices jdbc(DatabaseConfig config) {
        return create(new JdbcConnectionProvider(config), Clock.system(config.zone()));
    }

    public static ApplicationServices create(ConnectionProvider connections, Clock clock) {
        CustomerRepository customers = new JdbcCustomerRepository(connections);
        EventRepository events = new JdbcEventRepository(connections);
        BookingRepository bookings = new JdbcBookingRepository(connections);
        CustomerService customerService = new CustomerService(customers);
        EventService eventService = new EventService(events, clock);
        return new ApplicationServices(new BookingService(bookings, customerService, eventService, clock),
                customerService, eventService,
                new DatabaseMetadataService(new JdbcDatabaseMetadataRepository(connections)),
                new DatabasePreflight(connections), new ExcelBookingExporter(), clock);
    }
}
