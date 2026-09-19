package ru.ticketpoint;

import ru.ticketpoint.config.AppLogging;
import ru.ticketpoint.config.ConnectionProvider;
import ru.ticketpoint.config.DatabaseConfig;
import ru.ticketpoint.config.JdbcConnectionProvider;
import ru.ticketpoint.exception.BookingException;
import ru.ticketpoint.exception.DataAccessException;
import ru.ticketpoint.export.BookingExporter;
import ru.ticketpoint.export.ExcelBookingExporter;
import ru.ticketpoint.repository.BookingRepository;
import ru.ticketpoint.repository.CustomerRepository;
import ru.ticketpoint.repository.DatabaseMetadataRepository;
import ru.ticketpoint.repository.EventRepository;
import ru.ticketpoint.repository.JdbcBookingRepository;
import ru.ticketpoint.repository.JdbcCustomerRepository;
import ru.ticketpoint.repository.JdbcDatabaseMetadataRepository;
import ru.ticketpoint.repository.JdbcEventRepository;
import ru.ticketpoint.service.BookingService;
import ru.ticketpoint.service.CustomerService;
import ru.ticketpoint.service.DatabaseMetadataService;
import ru.ticketpoint.service.DatabasePreflight;
import ru.ticketpoint.service.EventService;
import ru.ticketpoint.ui.ConsoleUi;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Scanner;

public final class Main {
    private Main() { }

    public static void main(String[] args) {
        int code = run(args, new Scanner(System.in), new PrintWriter(System.out, true),
                new PrintWriter(System.err, true));
        if (code != 0) {
            System.exit(code);
        }
    }

    public static int run(String[] args, Scanner input, PrintWriter output, PrintWriter errors) {
        try {
            return execute(args, input, output, errors);
        } finally {
            output.flush();
            errors.flush();
        }
    }

    private static int execute(String[] args, Scanner input, PrintWriter output, PrintWriter errors) {
        if (args.length == 1 && "--help".equals(args[0])) {
            usage(output);
            return 0;
        }
        boolean checkOnly = args.length == 1 && "--check".equals(args[0]);
        Path export = null;
        if (args.length != 0 && !checkOnly) {
            if (args.length != 2 || !"--export".equals(args[0]) || args[1].isBlank()) {
                usage(errors);
                return 2;
            }
            try {
                export = Path.of(args[1]);
            } catch (InvalidPathException e) {
                errors.println("Некорректный путь экспорта.");
                usage(errors);
                return 2;
            }
        }
        DatabaseConfig config;
        try {
            config = DatabaseConfig.fromEnvironment();
        } catch (IllegalArgumentException e) {
            errors.println("Ошибка конфигурации: " + e.getMessage());
            return 1;
        }
        try (AppLogging logging = AppLogging.configure(config)) {
            try {
                ConnectionProvider connections = new JdbcConnectionProvider(config);
                new DatabasePreflight(connections).verify();
                if (checkOnly) {
                    output.println("Проверка конфигурации и схемы БД успешна.");
                    return 0;
                }
                Clock clock = Clock.system(config.zone());
                CustomerRepository customerRepository = new JdbcCustomerRepository(connections);
                EventRepository eventRepository = new JdbcEventRepository(connections);
                BookingRepository bookingRepository = new JdbcBookingRepository(connections);
                DatabaseMetadataRepository metadataRepository = new JdbcDatabaseMetadataRepository(connections);
                CustomerService customers = new CustomerService(customerRepository);
                EventService events = new EventService(eventRepository, clock);
                BookingService bookings = new BookingService(bookingRepository, customerRepository,
                        eventRepository, clock);
                DatabaseMetadataService metadata = new DatabaseMetadataService(metadataRepository);
                BookingExporter exporterService = new ExcelBookingExporter();
                if (export != null) {
                    exporterService.export(bookings.findAll(), export);
                    output.println("Экспорт сохранён: " + export.toAbsolutePath());
                } else {
                    new ConsoleUi(input, output, bookings, customers, events, metadata, exporterService, clock).run();
                }
                return 0;
            } catch (BookingException | IllegalArgumentException e) {
                if (e.getCause() instanceof java.sql.SQLException) {
                    AppLogging.technical("Выполнение команды", e);
                }
                errors.println("Ошибка: " + e.getMessage());
                return 1;
            } catch (DataAccessException e) {
                AppLogging.technical("Выполнение команды", e);
                errors.println("Ошибка базы данных: " + e.getMessage());
                return 1;
            } catch (IOException | java.sql.SQLException | RuntimeException e) {
                AppLogging.technical("Выполнение команды", e);
                errors.println("Не удалось выполнить команду. Подробности в журнале.");
                return 1;
            }
        }
    }

    private static void usage(PrintWriter output) {
        output.println("TicketPoint: без аргументов — интерактивное меню");
        output.println("  --help           показать справку");
        output.println("  --check          проверить конфигурацию и схему БД");
        output.println("  --export <путь>  экспортировать бронирования в Excel .xlsx");
    }
}
