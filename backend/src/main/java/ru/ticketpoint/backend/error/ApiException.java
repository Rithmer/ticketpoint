package ru.ticketpoint.backend.error;

public class ApiException extends RuntimeException {
    @java.io.Serial
    private static final long serialVersionUID = 1L;
    public final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public static ApiException bad(String message) {
        return new ApiException(400, message);
    }

    public static ApiException missing() {
        return new ApiException(404, "Запись не найдена");
    }

    public static ApiException unauthorized() {
        return new ApiException(401, "Неверные учётные данные или сеанс завершён");
    }

    public static void version(Long expected, long actual) {
        if (expected == null) throw bad("Запись устарела. Обновите список и повторите действие.");
        if (expected != actual) throw new ApiException(409, "Запись уже изменена. Обновите данные");
    }
}
