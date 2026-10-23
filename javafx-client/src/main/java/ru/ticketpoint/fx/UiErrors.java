package ru.ticketpoint.fx;

import java.io.IOException;
import java.sql.SQLException;

import javafx.scene.control.Alert;
import javafx.stage.Window;
import ru.ticketpoint.auth.AuthException;
import ru.ticketpoint.config.AppLogging;
import ru.ticketpoint.exception.BookingException;
import ru.ticketpoint.exception.DataAccessException;
import ru.ticketpoint.exception.EntityNotFoundException;

final class UiErrors {
    private UiErrors() {
    }

    static String message(Throwable error) {
        AppLogging.technical("Операция JavaFX", error);
        return switch (error) {
            case AuthException auth -> auth.getMessage();
            case EntityNotFoundException missing ->
                    missing.getMessage() + ". Обновите список и выберите запись заново.";
            case IllegalArgumentException invalid -> validationMessage(invalid);
            case BookingException booking -> booking.getMessage();
            case SQLException sql -> databaseMessage(sql);
            case DataAccessException database -> databaseMessage(database);
            case IOException ignored ->
                    "Не удалось прочитать или сохранить файл. Проверьте путь, права доступа и свободное место.";
            case null, default ->
                    "Не удалось выполнить операцию. Повторите действие. Если ошибка повторяется, перезапустите приложение.";
        };
    }

    private static String validationMessage(IllegalArgumentException error) {
        String message = error.getMessage();
        if (message != null && message.matches("(?s).*(DB_|APP_ZONE|BOOTSTRAP_|POSTGRES_|\\.env|JDBC).*"))
            return "Не удалось настроить подключение к серверу. Обратитесь к администратору и попробуйте войти ещё раз.";
        return message != null && message.matches("(?s).*[А-Яа-яЁё].*")
                ? message : "Проверьте введённые данные и повторите действие.";
    }

    private static String databaseMessage(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                String message = sqlStateMessage(sql.getSQLState());
                if (message != null) {
                    return message;
                }
            }
        }
        return "Не удалось загрузить или сохранить данные. Обновите список и повторите действие.";
    }

    private static String sqlStateMessage(String state) {
        return switch (state) {
            case "3D000", "42P01", "42703", "42704" -> "Не удалось загрузить данные. "
                    + "Обратитесь к администратору, чтобы обновить приложение и данные.";
            case "42501" -> "Недостаточно прав для загрузки данных. Обратитесь к администратору.";
            case "57014" -> "База данных не ответила вовремя. Подождите немного и повторите действие.";
            case String code when code.startsWith("08") || code.equals("57P01") || code.equals("57P03") ->
                    "Нет соединения с сервером. Проверьте подключение "
                            + "и нажмите «Обновить».";
            case String code when code.startsWith("28") ->
                    "Не удалось подключиться к серверу. Обратитесь к администратору "
                            + "и перезапустите приложение.";
            case null, default -> null;
        };
    }

    static void show(Window owner, Throwable error) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(owner);
        alert.setTitle("Ошибка");
        alert.setHeaderText("Операция не выполнена");
        alert.setContentText(message(error));
        alert.showAndWait();
    }
}
