package ru.ticketpoint;

import org.junit.jupiter.api.Test;
import ru.ticketpoint.config.DatabaseConfig;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseConfigTest {
    @Test
    void requiresNonBlankDatabasePassword() {
        assertThrows(IllegalArgumentException.class, () -> DatabaseConfig.fromEnvironment(Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> DatabaseConfig.fromEnvironment(Map.of("DB_PASSWORD", "   ")));
    }

    @Test
    void doesNotExposeConnectionCredentialsInToString() {
        Map<String, String> environment = new HashMap<>();
        environment.put("DB_URL", "jdbc:postgresql://db.internal:5432/private_database");
        environment.put("DB_USER", "private_user");
        environment.put("DB_PASSWORD", "Private-Password-123");
        String text = DatabaseConfig.fromEnvironment(environment).toString();

        assertFalse(text.contains("Private-Password-123"));
        assertFalse(text.contains("private_user"));
        assertFalse(text.contains("db.internal"));
        assertFalse(text.contains("private_database"));
    }
}
