package ru.ticketpoint.repository;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import ru.ticketpoint.exception.BookingException;
import ru.ticketpoint.exception.DataAccessException;

import static org.junit.jupiter.api.Assertions.*;

class SqlExceptionTranslatorTest {
    @Test
    void constraintFailureKeepsItsCauseAndExplainsTheConflict() {
        SQLException sql = new SQLException("Internal constraint details", "23505");

        BookingException error = assertInstanceOf(BookingException.class,
                SqlExceptionTranslator.translate("Создание бронирования", sql));

        assertSame(sql, error.getCause());
        assertTrue(error.getMessage().contains("уже существует"));
        assertFalse(error.getMessage().contains("Internal constraint details"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"08001", "ZZ999"})
    void unclassifiedFailureKeepsOperationAndCause(String state) {
        SQLException sql = new SQLException("Internal connection details", state);

        DataAccessException error = assertInstanceOf(DataAccessException.class,
                SqlExceptionTranslator.translate("Загрузка бронирований", sql));

        assertSame(sql, error.getCause());
        assertTrue(error.getMessage().startsWith("Загрузка бронирований"));
    }
}
