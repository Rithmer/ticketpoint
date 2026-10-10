package ru.ticketpoint.fx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.ticketpoint.exception.DataAccessException;
import ru.ticketpoint.exception.EntityNotFoundException;

import java.io.IOException;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class UiErrorsTest {
    @ParameterizedTest
    @CsvSource({"08001, PostgreSQL", "08006, PostgreSQL", "57P01, PostgreSQL", "57P03, PostgreSQL",
            "28P01, пароль", "28000, пароль", "3D000, таблиц", "42P01, таблиц", "42703, таблиц",
            "42704, таблиц", "42501, прав", "57014, вовремя"})
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
