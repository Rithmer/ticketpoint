package ru.ticketpoint.backend;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ru.ticketpoint.exception.BookingException;
import ru.ticketpoint.exception.DataAccessException;
import ru.ticketpoint.exception.EntityNotFoundException;

import java.util.logging.Level;
import java.util.logging.Logger;

@RestControllerAdvice
public final class ApiErrors {
    private static final Logger LOG = Logger.getLogger(ApiErrors.class.getName());
    @ExceptionHandler(ru.ticketpoint.auth.AuthException.class)
    ResponseEntity<ErrorMessage> auth(ru.ticketpoint.auth.AuthException error) {
        return response(error.forbidden() ? HttpStatus.FORBIDDEN : HttpStatus.UNAUTHORIZED, error.getMessage());
    }
    @ExceptionHandler(EntityNotFoundException.class)
    ResponseEntity<ErrorMessage> missing(EntityNotFoundException error) {
        return response(HttpStatus.NOT_FOUND, error.getMessage());
    }
    @ExceptionHandler({IllegalArgumentException.class, BookingException.class})
    ResponseEntity<ErrorMessage> invalid(RuntimeException error) {
        return response(HttpStatus.BAD_REQUEST, error.getMessage());
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MethodArgumentNotValidException.class})
    ResponseEntity<ErrorMessage> malformed(Exception error) {
        return response(HttpStatus.BAD_REQUEST, "Проверьте обязательные поля, формат даты и чисел.");
    }
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ErrorMessage> database(DataAccessException error) {
        LOG.log(Level.SEVERE, "Ошибка доступа к PostgreSQL", error);
        return response(HttpStatus.SERVICE_UNAVAILABLE, "База данных недоступна. Проверьте подключение и повторите действие.");
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorMessage> unexpected(Exception error) {
        LOG.log(Level.SEVERE, "Ошибка REST API", error);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось выполнить операцию. Подробности в журнале сервера.");
    }
    private static ResponseEntity<ErrorMessage> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorMessage(message));
    }
    public record ErrorMessage(String message) { }
}
