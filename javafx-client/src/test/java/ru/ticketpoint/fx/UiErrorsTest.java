package ru.ticketpoint.fx;

import java.io.IOException;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.ticketpoint.exception.DataAccessException;
import ru.ticketpoint.exception.EntityNotFoundException;

import static org.junit.jupiter.api.Assertions.*;

class UiErrorsTest {
    @Test
    void connectionConfigurationDoesNotAskBuyerToEditEnvironmentFiles() {
        String message = UiErrors.message(new IllegalArgumentException("Заполните DB_PASSWORD в .env"));
        assertTrue(message.contains("администратору"));
        assertFalse(message.contains("DB_PASSWORD"));
        assertFalse(message.contains(".env"));
    }

    @Test
    void unexpectedValidationErrorDoesNotExposeEnglishImplementationDetails() {
        String message = UiErrors.message(new IllegalArgumentException("Internal configuration value"));
        assertEquals("Проверьте введённые данные и повторите действие.", message);
    }

    @ParameterizedTest
    @CsvSource({"08001, соединения", "08006, соединения", "57P01, соединения", "57P03, соединения",
            "28P01, подключиться", "28000, подключиться", "3D000, загрузить", "42P01, загрузить", "42703, загрузить",
            "42704, загрузить", "42501, прав", "57014, вовремя"})
    void explainsDatabaseFailureWithoutShowingTechnicalDetails(String state, String expected) {
        SQLException sql = new SQLException("SELECT secret FROM internal_table", state);
        String message = UiErrors.message(new DataAccessException("Internal failure", sql));
        assertTrue(message.contains(expected));
        assertFalse(message.contains("SELECT"));
        assertFalse(message.contains("internal_table"));
        assertFalse(message.contains(state));
    }

    @Test
    void handlesUnknownDatabaseFailureWithoutExposingSql() {
        String message = UiErrors.message(new SQLException("internal_table is broken"));
        assertTrue(message.contains("Обновите список"));
        assertFalse(message.contains("internal_table"));
    }

    @Test
    void findsConnectionFailureBehindAnUnclassifiedSqlException() {
        SQLException connection = new SQLException("Private connection details", "08006");
        SQLException wrapper = new SQLException("Private wrapper details", "ZZ999", connection);

        String message = UiErrors.message(new DataAccessException("Internal operation", wrapper));

        assertTrue(message.contains("Нет соединения"));
        assertFalse(message.contains("Private"));
        assertFalse(message.contains("08006"));
    }

    @Test
    void missingRecordSuggestsRefreshingTheList() {
        String message = UiErrors.message(new EntityNotFoundException("Бронирование не найдено"));
        assertTrue(message.contains("Бронирование не найдено"));
        assertTrue(message.contains("Обновите список"));
    }

    @Test
    void fileFailureOffersActionWithoutExposingException() {
        String message = UiErrors.message(new IOException("Private path"));
        assertTrue(message.contains("права доступа"));
        assertFalse(message.contains("Private path"));
    }
}
