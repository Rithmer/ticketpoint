package ru.ticketpoint.fx;

import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;

import static org.junit.jupiter.api.Assertions.*;

class UiResourcesTest {
    @Test
    void packagedInterfaceResourcesAreAvailable() throws Exception {
        for (String resource : new String[]{"main.fxml", "bookings.fxml", "references.fxml", "statistics.fxml",
                "login.fxml", "password-dialog.fxml", "users.fxml", "user-dialog.fxml", "booking-dialog.fxml", "booking-details.fxml", "customer-dialog.fxml", "event-dialog.fxml", "ticketpoint.css"}) {
            try (var stream = UiResources.location(resource).openStream()) {
                assertTrue(stream.read() >= 0, resource);
            }
        }
        assertEquals(UiResources.location("ticketpoint.css").toExternalForm(), UiResources.stylesheet());
    }

    @Test
    void missingResourceReportsItsName() {
        FileNotFoundException error = assertThrows(FileNotFoundException.class,
                () -> UiResources.location("missing.fxml"));
        assertTrue(error.getMessage().contains("missing.fxml"));
    }
}
