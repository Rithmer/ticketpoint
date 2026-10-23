package ru.ticketpoint.config;

import java.time.Clock;
import java.util.Objects;
import java.util.function.Supplier;

import ru.ticketpoint.auth.AuthService;
import ru.ticketpoint.auth.User;
import ru.ticketpoint.export.BookingExporter;
import ru.ticketpoint.export.ExcelBookingExporter;
import ru.ticketpoint.repository.*;
import ru.ticketpoint.service.*;

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
        return create(connections, clock, AccessPolicy.TRUSTED);
    }

    public static ApplicationServices secured(DatabaseConfig config, AuthService auth,
                                              Supplier<User> user) {
        return create(new JdbcConnectionProvider(config), Clock.system(config.zone()),
                new AccessPolicy(() -> auth.requireRead(user.get()), () -> auth.requireWrite(user.get()),
                        () -> auth.current(user.get())));
    }

    private static ApplicationServices create(ConnectionProvider connections, Clock clock, AccessPolicy access) {
        CustomerRepository customers = new JdbcCustomerRepository(connections);
        EventRepository events = new JdbcEventRepository(connections);
        BookingRepository bookings = new JdbcBookingRepository(connections);
        CustomerService customerService = new CustomerService(customers, access);
        EventService eventService = new EventService(events, clock, access);
        return new ApplicationServices(new BookingService(bookings, customerService, eventService, clock, access),
                customerService, eventService,
                new DatabaseMetadataService(new JdbcDatabaseMetadataRepository(connections)),
                new DatabasePreflight(connections), (rows, path) -> {
            access.read().run();
            for (var row : rows) access.requireCustomer(row.customer().id());
            new ExcelBookingExporter().export(rows, path);
        }, clock);
    }
}
