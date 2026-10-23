package ru.ticketpoint;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import ru.ticketpoint.config.ConnectionProvider;
import ru.ticketpoint.export.ExcelBookingExporter;
import ru.ticketpoint.repository.JdbcBookingRepository;
import ru.ticketpoint.repository.JdbcCustomerRepository;
import ru.ticketpoint.repository.JdbcDatabaseMetadataRepository;
import ru.ticketpoint.repository.JdbcEventRepository;
import ru.ticketpoint.service.BookingService;
import ru.ticketpoint.service.CustomerService;
import ru.ticketpoint.service.DatabaseMetadataService;
import ru.ticketpoint.service.EventService;
import ru.ticketpoint.ui.ConsoleUi;

import static org.junit.jupiter.api.Assertions.*;

class ConsoleUiTest {
    private final AtomicInteger databaseCalls = new AtomicInteger();

    @Test
    void exitsOnEofIncludingMidOperation() {
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            assertTrue(run("").contains("Работа завершена"));
            assertTrue(run("3\n1\n").contains("Ввод завершён"));
        });
    }

    @Test
    void rejectsImpossibleDateBeforeDatabaseCall() {
        String output = run("2\n2\nКонцерт\nЗал\n31.02.2030 12:00\n0\n0\n");
        assertTrue(output.contains("Некорректная дата"));
        assertEquals(0, databaseCalls.get());
    }

    @Test
    void rejectsNonNumericIdAndKeepsSubmenu() {
        String output = run("3\n3\nabc\n0\n0\n");
        assertTrue(output.contains("Номер записи должен быть целым числом"));
        assertEquals(2, output.split("=== Бронирования ===", -1).length - 1);
        assertEquals(0, databaseCalls.get());
    }

    @Test
    void invalidSubmenuDoesNotSelectAnOperation() {
        assertTrue(run("4\n77\n0\n0\n").contains("Неизвестный пункт"));
        assertEquals(0, databaseCalls.get());
    }

    @Test
    void reportsDatabaseErrorsAndContinues() {
        String output = run("3\n2\n0\n0\n");
        assertTrue(output.contains("Ошибка базы данных"));
        assertTrue(output.contains("Работа завершена"));
        assertEquals(1, databaseCalls.get());
    }

    private String run(String commands) {
        ConnectionProvider provider = () -> {
            databaseCalls.incrementAndGet();
            throw new SQLException("Simulated unavailable DB", "08001");
        };
        var customerRepository = new JdbcCustomerRepository(provider);
        var eventRepository = new JdbcEventRepository(provider);
        CustomerService customers = new CustomerService(customerRepository);
        EventService events = new EventService(eventRepository, Clock.systemUTC());
        BookingService bookings = new BookingService(new JdbcBookingRepository(provider), customers,
                events, Clock.systemUTC());
        DatabaseMetadataService metadata = new DatabaseMetadataService(
                new JdbcDatabaseMetadataRepository(provider));
        StringWriter text = new StringWriter();
        new ConsoleUi(new Scanner(commands), new PrintWriter(text), bookings, customers, events, metadata,
                new ExcelBookingExporter(), Clock.systemUTC()).run();
        return text.toString();
    }
}
