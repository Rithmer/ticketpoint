package ru.ticketpoint.ui;

import java.io.PrintWriter;
import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.Objects;
import java.util.Scanner;

import ru.ticketpoint.model.BookingStatus;

final class ConsoleInput {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm")
            .withResolverStyle(ResolverStyle.STRICT);

    private final Scanner scanner;
    private final PrintWriter output;

    ConsoleInput(Scanner scanner, PrintWriter output) {
        this.scanner = Objects.requireNonNull(scanner);
        this.output = Objects.requireNonNull(output);
    }

    String read(String prompt) {
        output.print(prompt);
        output.flush();
        if (!scanner.hasNextLine()) {
            throw new EndOfInputException();
        }
        return scanner.nextLine().strip();
    }

    long readId(String prompt) {
        try {
            return Long.parseLong(read(prompt));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Номер записи должен быть целым числом", e);
        }
    }

    BookingStatus readStatus() {
        try {
            return switch (read("Статус (Новое, Подтверждено, Оплачено, Отменено): ").toLowerCase(Locale.ROOT)) {
                case "новое", "новая заявка" -> BookingStatus.NEW;
                case "подтверждено" -> BookingStatus.CONFIRMED;
                case "оплачено" -> BookingStatus.PAID;
                case "отменено" -> BookingStatus.CANCELLED;
                default -> throw new IllegalArgumentException();
            };
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Укажите один из статусов: новое, подтверждено, оплачено или отменено.", e);
        }
    }

    LocalDateTime readDateTime() {
        try {
            return LocalDateTime.parse(read("Дата и время (дд.ММ.гггг чч:мм): "), DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Некорректная дата. Пример: 25.12.2030 19:30", e);
        }
    }

    BigDecimal readPrice() {
        try {
            return new BigDecimal(read("Цена, руб.: ").replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Цена должна быть числом", e);
        }
    }

    static final class EndOfInputException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;
    }
}
