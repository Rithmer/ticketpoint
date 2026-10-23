package ru.ticketpoint.ui;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;

import ru.ticketpoint.config.AppLogging;
import ru.ticketpoint.exception.BookingException;
import ru.ticketpoint.exception.DataAccessException;
import ru.ticketpoint.export.BookingExporter;
import ru.ticketpoint.model.CreateCustomerCommand;
import ru.ticketpoint.model.CreateEventCommand;
import ru.ticketpoint.service.BookingService;
import ru.ticketpoint.service.CustomerService;
import ru.ticketpoint.service.DatabaseMetadataService;
import ru.ticketpoint.service.EventService;

public final class ConsoleUi {
    private static final DateTimeFormatter EXPORT_TIMESTAMP = DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss-SSS");
    private final ConsoleInput input;
    private final PrintWriter output;
    private final BookingService bookings;
    private final CustomerService customers;
    private final EventService events;
    private final DatabaseMetadataService metadata;
    private final BookingExporter exporter;
    private final Clock clock;

    public ConsoleUi(Scanner input, PrintWriter output, BookingService bookings, CustomerService customers,
                     EventService events, DatabaseMetadataService metadata, BookingExporter exporter, Clock clock) {
        this.input = new ConsoleInput(input, output);
        this.output = Objects.requireNonNull(output);
        this.bookings = Objects.requireNonNull(bookings);
        this.customers = Objects.requireNonNull(customers);
        this.events = Objects.requireNonNull(events);
        this.metadata = Objects.requireNonNull(metadata);
        this.exporter = Objects.requireNonNull(exporter);
        this.clock = Objects.requireNonNull(clock);
    }

    public void run() {
        try {
            menu("Билетный оператор TicketPoint", """
                    1. Клиенты
                    2. Мероприятия
                    3. Бронирования
                    4. Поиск
                    5. Фильтрация
                    6. Сортировка
                    7. Статистика
                    8. Экспорт в Excel
                    9. Таблицы базы данных
                    0. Выход
                    """, command -> {
                switch (command) {
                    case "1" -> customersMenu();
                    case "2" -> eventsMenu();
                    case "3" -> bookingsMenu();
                    case "4" -> searchMenu();
                    case "5" -> filterMenu();
                    case "6" -> sortMenu();
                    case "7" -> statistics();
                    case "8" -> export();
                    case "9" -> print(metadata.tableNames());
                    default -> unknown();
                }
            });
        } catch (ConsoleInput.EndOfInputException e) {
            output.println("\nВвод завершён.");
        }
        output.println("Работа завершена.");
        output.flush();
    }

    private void customersMenu() {
        menu("Клиенты", "1. Список\n2. Создать\n0. Назад", command -> {
            switch (command) {
                case "1" -> print(customers.findAll());
                case "2" -> printItem(customers.create(new CreateCustomerCommand(input.read("Имя: "))));
                default -> unknown();
            }
        });
    }

    private void eventsMenu() {
        menu("Мероприятия", "1. Список\n2. Создать\n0. Назад", command -> {
            switch (command) {
                case "1" -> print(events.findAll());
                case "2" -> createEvent();
                default -> unknown();
            }
        });
    }

    private void bookingsMenu() {
        menu("Бронирования", """
                1. Создать по ID клиента и мероприятия
                2. Список
                3. Получить по ID
                4. Изменить статус
                5. Удалить
                0. Назад
                """, command -> {
            switch (command) {
                case "1" -> printItem(bookings.createBooking(input.readId("ID клиента: "),
                        input.readId("ID мероприятия: ")));
                case "2" -> print(bookings.findAll());
                case "3" -> printItem(bookings.findById(input.readId("ID бронирования: ")));
                case "4" -> printItem(bookings.changeStatus(input.readId("ID бронирования: "),
                        input.readStatus()));
                case "5" -> {
                    bookings.delete(input.readId("ID бронирования: "));
                    output.println("Бронирование удалено.");
                }
                default -> unknown();
            }
        });
    }

    private void searchMenu() {
        menu("Поиск", "1. Номер билета\n2. Название мероприятия\n0. Назад", command -> {
            switch (command) {
                case "1" -> printItem(bookings.findByTicketNumber(input.read("Номер: ")));
                case "2" -> print(bookings.searchByEventTitle(input.read("Часть названия: ")));
                default -> unknown();
            }
        });
    }

    private void filterMenu() {
        menu("Фильтрация", "1. По статусу\n2. По клиенту\n0. Назад", command -> {
            switch (command) {
                case "1" -> print(bookings.filterByStatus(input.readStatus()));
                case "2" -> print(bookings.filterByCustomer(input.readId("ID клиента: ")));
                default -> unknown();
            }
        });
    }

    private void sortMenu() {
        menu("Сортировка", "1. По созданию (новые сначала)\n2. По дате мероприятия\n0. Назад", command -> {
            switch (command) {
                case "1" -> print(bookings.sortByCreatedAt());
                case "2" -> print(bookings.sortByEventDate());
                default -> unknown();
            }
        });
    }

    private void createEvent() {
        String title = input.read("Название: ");
        String venue = input.read("Место проведения: ");
        LocalDateTime date = input.readDateTime();
        printItem(events.create(new CreateEventCommand(title, venue, date,
                input.readPrice())));
    }

    private void statistics() {
        var statistics = bookings.statistics();
        output.printf("Всего бронирований: %d%nНовых: %d%nОплаченных: %d%nОтменённых: %d%nБилетов от 5000 руб.: %d%n",
                statistics.total(), statistics.newBookings(), statistics.paid(), statistics.cancelled(),
                statistics.expensiveTickets());
    }

    private void export() throws IOException {
        String name = "bookings-" + EXPORT_TIMESTAMP.format(LocalDateTime.now(clock)) + ".xlsx";
        Path path = Path.of("exports", name);
        exporter.export(bookings.findAll(), path);
        output.println("Экспорт сохранён: " + path.toAbsolutePath());
    }

    private void menu(String title, String options, Action action) {
        while (true) {
            output.println("\n=== " + title + " ===");
            output.println(options);
            String command = input.read("Выберите пункт: ");
            if ("0".equals(command)) {
                return;
            }
            try {
                action.run(command);
            } catch (BookingException | IllegalArgumentException e) {
                if (e.getCause() instanceof SQLException) {
                    AppLogging.technical("Операция меню: " + title, e);
                }
                output.println("Ошибка: " + e.getMessage());
            } catch (DataAccessException e) {
                AppLogging.technical("Операция меню: " + title, e);
                output.println("Ошибка базы данных: " + e.getMessage());
            } catch (IOException e) {
                AppLogging.technical("Экспорт из меню", e);
                output.println("Ошибка экспорта: не удалось записать файл. Подробности в журнале.");
            }
        }
    }

    private void print(List<?> items) {
        if (items.isEmpty()) {
            output.println("Записи не найдены.");
        } else {
            items.forEach(this::printItem);
        }
    }

    private void printItem(Object item) {
        output.println(ConsoleFormatter.format(item));
    }

    private void unknown() {
        output.println("Неизвестный пункт меню.");
    }

    @FunctionalInterface
    private interface Action {
        void run(String command) throws IOException;
    }
}
