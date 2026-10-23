package ru.ticketpoint;

import org.junit.jupiter.api.Test;
import ru.ticketpoint.auth.AuthException;
import ru.ticketpoint.config.ApplicationServices;
import ru.ticketpoint.config.DatabaseConfig;

import static org.junit.jupiter.api.Assertions.assertThrows;

class GuestAccessTest {
    @Test
    void guestCannotMutateBookingsOrReferenceDataEvenWithoutUi() {
        var services = ApplicationServices.guest(new DatabaseConfig("jdbc:postgresql://localhost/unused", "test", "test",
                java.time.ZoneId.of("Europe/Moscow"), 5, 15, 30));
        assertThrows(AuthException.class, () -> services.customers().create(null));
        assertThrows(AuthException.class, () -> services.events().create(null));
        assertThrows(AuthException.class, () -> services.bookings().delete(1));
    }
}
