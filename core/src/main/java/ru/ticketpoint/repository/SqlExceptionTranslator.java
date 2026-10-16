package ru.ticketpoint.repository;

import ru.ticketpoint.exception.BookingException;
import ru.ticketpoint.exception.DataAccessException;

import java.sql.SQLException;

final class SqlExceptionTranslator {
    private SqlExceptionTranslator() { }

    static RuntimeException translate(String operation, SQLException error) {
        String state = error.getSQLState();
        return switch (state) {
            case "23505" -> new BookingException(
                    "Код клиента или номер билета уже существует. Повторите создание записи", error);
            case "23503" -> new BookingException(
                    "Клиент или мероприятие недоступны либо используются в бронировании. "
                            + "Обновите список и проверьте выбранные записи", error);
            case "23001" -> new BookingException(
                    "Запись используется в бронировании и не может быть удалена", error);
            case "23514", "22001", "22003" -> new BookingException(
                    "Не удалось сохранить данные. Проверьте заполнение обязательных полей, "
                            + "длину текста и цену", error);
            case null, default -> new DataAccessException(
                    operation + ". Проверьте подключение и SQL-схему (SQLState: " + state + ")", error);
        };
    }
}
