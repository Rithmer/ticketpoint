package ru.ticketpoint.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class EnvironmentFileTest {
    @TempDir
    Path directory;

    @Test
    void readsDatabaseSettingsAndReferencesFromFile() throws Exception {
        Path file = directory.resolve(".env");
        Files.writeString(file, """
                # Database settings
                POSTGRES_USER=ticketpoint
                POSTGRES_PASSWORD='test-password'
                DB_USER=${POSTGRES_USER}
                DB_PASSWORD=${POSTGRES_PASSWORD}
                APP_ZONE="Europe/Moscow"
                """);
        DatabaseConfig config = DatabaseConfig.fromEnvironment(EnvironmentFile.read(file, Map.of()));
        assertEquals("ticketpoint", config.user());
        assertEquals("test-password", config.password());
        assertEquals("Europe/Moscow", config.zone().getId());
    }

    @Test
    void environmentOverridesFileAndIsUsedInReferences() throws Exception {
        Path file = directory.resolve(".env");
        Files.writeString(file, "POSTGRES_PASSWORD=local\nDB_PASSWORD=${POSTGRES_PASSWORD}\nDB_USER=local\n");
        Map<String, String> values = EnvironmentFile.read(file,
                Map.of("POSTGRES_PASSWORD", "external", "DB_USER", "external-user"));
        assertEquals("external", values.get("DB_PASSWORD"));
        assertEquals("external-user", values.get("DB_USER"));
    }

    @Test
    void singleQuotesPreservePasswordCharacters() throws Exception {
        Path file = directory.resolve(".env");
        Files.writeString(file, "DB_PASSWORD='abc${LITERAL}#=$\\word'\n");
        assertEquals("abc${LITERAL}#=$\\word", EnvironmentFile.read(file, Map.of()).get("DB_PASSWORD"));
    }

    @Test
    void missingFileStillAllowsEnvironmentSettings() {
        Map<String, String> values = Map.of("DB_PASSWORD", "external");
        assertEquals(values, EnvironmentFile.read(directory.resolve("missing.env"), values));
    }

    @Test
    void malformedLineReportsNumberWithoutExposingValue() throws Exception {
        Path file = directory.resolve(".env");
        Files.writeString(file, "# settings\nprivate-secret\n");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> EnvironmentFile.read(file, Map.of()));
        assertTrue(error.getMessage().contains("2"));
        assertFalse(error.getMessage().contains("private-secret"));
    }

    @Test
    void missingReferenceReportsItsName() throws Exception {
        Path file = directory.resolve(".env");
        Files.writeString(file, "DB_PASSWORD=${POSTGRES_PASSWORD}\n");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> EnvironmentFile.read(file, Map.of()));
        assertTrue(error.getMessage().contains("POSTGRES_PASSWORD"));
    }
}
