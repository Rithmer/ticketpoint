package ru.ticketpoint;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Scanner;

import ru.ticketpoint.config.AppLogging;
import ru.ticketpoint.config.ApplicationServices;
import ru.ticketpoint.config.DatabaseConfig;
import ru.ticketpoint.exception.BookingException;
import ru.ticketpoint.exception.DataAccessException;
import ru.ticketpoint.ui.ConsoleUi;

public final class ConsoleApplication {
    private ConsoleApplication() {
    }

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
        AppLogging logging = AppLogging.configure(config);
        try (logging) {
            return executeCommand(input, output, errors, config, checkOnly, export);
        }
    }

    private static int executeCommand(Scanner input, PrintWriter output, PrintWriter errors,
                                      DatabaseConfig config, boolean checkOnly, Path export) {
        try {
            ApplicationServices services = ApplicationServices.jdbc(config);
            services.preflight().verify();
            if (checkOnly) {
                output.println("Проверка конфигурации и схемы БД успешна.");
                return 0;
            }
            if (export != null) {
                services.exporter().export(services.bookings().findAll(), export);
                output.println("Экспорт сохранён: " + export.toAbsolutePath());
            } else {
                new ConsoleUi(input, output, services.bookings(), services.customers(), services.events(),
                        services.metadata(), services.exporter(), services.clock()).run();
            }
            return 0;
        } catch (BookingException | IllegalArgumentException e) {
            if (e.getCause() instanceof SQLException) {
                AppLogging.technical("Выполнение команды", e);
            }
            errors.println("Ошибка: " + e.getMessage());
            return 1;
        } catch (DataAccessException e) {
            AppLogging.technical("Выполнение команды", e);
            errors.println("Ошибка базы данных: " + e.getMessage());
            return 1;
        } catch (IOException | SQLException | RuntimeException e) {
            AppLogging.technical("Выполнение команды", e);
            errors.println("Не удалось выполнить команду. Подробности в журнале.");
            return 1;
        }
    }

    private static void usage(PrintWriter output) {
        output.println("TicketPoint: без аргументов — интерактивное меню");
        output.println("  --help           показать справку");
        output.println("  --check          проверить конфигурацию и схему БД");
        output.println("  --export <путь>  экспортировать бронирования в Excel .xlsx");
    }
}
