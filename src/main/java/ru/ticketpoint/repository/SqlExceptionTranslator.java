package ru.ticketpoint.repository;

import ru.ticketpoint.exception.BookingException;
import ru.ticketpoint.exception.DataAccessException;

import java.sql.SQLException;

final class SqlExceptionTranslator {
    private SqlExceptionTranslator() { }

    static RuntimeException translate(String operation, SQLException error) {
        String state = error.getSQLState();
        if ("23505".equals(state)) {
            return new BookingException("Код клиента или номер билета уже существует", error);
        }
        if ("23503".equals(state) || "23001".equals(state)) {
            return new BookingException("Связанная запись не существует или используется", error);
        }
        if ("23514".equals(state) || "22001".equals(state) || "22003".equals(state)) {
            return new BookingException("Данные нарушают ограничения базы данных", error);
        }
        return new DataAccessException(operation + ". Проверьте подключение и SQL-схему (SQLState: "
                + state + ")", error);
    }
}
