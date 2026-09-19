package ru.ticketpoint.config;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class AppLogging implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger("ru.ticketpoint");
    private final Handler handler;
    private final Map<Logger, Logger> libraryParents;
    private final Map<Logger, Boolean> libraryPropagation;

    static {
        LOGGER.setUseParentHandlers(false);
        Handler fallback = new ConsoleHandler();
        fallback.setFormatter(new SafeFormatter("", ""));
        LOGGER.addHandler(fallback);
    }

    private AppLogging(Handler handler, Map<Logger, Logger> libraryParents,
                       Map<Logger, Boolean> libraryPropagation) {
        this.handler = handler;
        this.libraryParents = libraryParents;
        this.libraryPropagation = libraryPropagation;
    }

    public static AppLogging configure(DatabaseConfig config) {
        Handler handler;
        Exception failure = null;
        try {
            Files.createDirectories(Path.of("logs"));
            handler = new FileHandler("logs/ticketpoint-%g.log", 1_048_576, 3, true);
            handler.setEncoding("UTF-8");
        } catch (IOException | SecurityException e) {
            handler = new ConsoleHandler();
            failure = e;
        }
        handler.setFormatter(new SafeFormatter(config.url(), config.password()));
        for (Handler old : LOGGER.getHandlers()) {
            LOGGER.removeHandler(old);
            old.close();
        }
        LOGGER.addHandler(handler);
        Map<Logger, Logger> parents = new LinkedHashMap<>();
        Map<Logger, Boolean> propagation = new LinkedHashMap<>();
        for (String name : List.of("org.postgresql", "org.apache.poi", "org.apache.xmlbeans")) {
            Logger library = Logger.getLogger(name);
            parents.put(library, library.getParent());
            propagation.put(library, library.getUseParentHandlers());
            library.setParent(LOGGER);
            library.setUseParentHandlers(true);
        }
        if (failure != null) {
            technical("Журнал недоступен; используется stderr", failure);
        }
        return new AppLogging(handler, parents, propagation);
    }

    public static void technical(String operation, Throwable error) {
        LOGGER.log(Level.SEVERE, operation, error);
    }

    @Override
    public void close() {
        libraryParents.forEach(Logger::setParent);
        libraryPropagation.forEach(Logger::setUseParentHandlers);
        LOGGER.removeHandler(handler);
        handler.close();
    }

    private static final class SafeFormatter extends Formatter {
        private final String url;
        private final String password;

        private SafeFormatter(String url, String password) {
            this.url = url;
            this.password = password;
        }

        @Override
        public String format(LogRecord record) {
            StringWriter text = new StringWriter();
            PrintWriter writer = new PrintWriter(text);
            writer.printf("%s %s %s%n", record.getInstant(), record.getLevel(), formatMessage(record));
            if (record.getThrown() != null) {
                Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
                sqlStates(record.getThrown(), writer, seen);
                record.getThrown().printStackTrace(writer);
            }
            writer.flush();
            String result = text.toString();
            if (!url.isEmpty()) {
                result = result.replace(url, "<JDBC URL hidden>");
            }
            result = result.replaceAll("jdbc:postgresql:\\S+", "<JDBC URL hidden>");
            result = result.replaceAll("(?i)(password\\s*[=:]\\s*)\\S+", "$1<hidden>");
            if (!password.isEmpty()) {
                result = result.replace(password, "<hidden>");
            }
            return result;
        }

        private static void sqlStates(Throwable error, PrintWriter writer, Set<Throwable> seen) {
            if (error == null || !seen.add(error)) {
                return;
            }
            if (error instanceof SQLException sql) {
                writer.println("SQLState: " + sql.getSQLState());
                sqlStates(sql.getNextException(), writer, seen);
            }
            sqlStates(error.getCause(), writer, seen);
            for (Throwable suppressed : error.getSuppressed()) {
                sqlStates(suppressed, writer, seen);
            }
        }
    }
}
