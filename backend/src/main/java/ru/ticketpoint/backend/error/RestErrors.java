package ru.ticketpoint.backend.error;

import jakarta.servlet.http.HttpServletRequest;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class RestErrors {
    private static final Logger LOG = LoggerFactory.getLogger(RestErrors.class);

    public record ErrorBody(
            int status, String code, String message, String correlationId, Map<String, String> fields) {
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> handle(Exception ex, HttpServletRequest request) {
        int status = 500;
        String message = "Не удалось выполнить действие. Попробуйте ещё раз немного позже.";
        Map<String, String> fields = new LinkedHashMap<>();
        if (ex instanceof ApiException a) {
            status = a.status;
            message = a.getMessage();
        } else if (ex instanceof MethodArgumentNotValidException v) {
            status = 400;
            message = "Проверьте поля формы";
            v.getBindingResult()
                    .getFieldErrors()
                    .forEach(e -> fields.put(e.getField(), validationMessage(e)));
        } else if (ex instanceof IllegalArgumentException) {
            status = 400;

            message = ex.getMessage() != null && ex.getMessage().matches("(?s).*[А-Яа-яЁё].*")
                    ? ex.getMessage() : "Проверьте введённые данные и повторите действие.";
        } else if (ex instanceof HttpMessageNotReadableException
                || ex instanceof MethodArgumentTypeMismatchException
                || ex instanceof MaxUploadSizeExceededException) {
            status = 400;
            message = "Не удалось прочитать данные. Проверьте поля формы. Размер файла не должен превышать 2 МБ.";
        } else if (ex instanceof OptimisticLockingFailureException) {
            status = 409;
            message = "Запись уже изменена. Обновите данные";
        } else if (ex instanceof DataIntegrityViolationException) {
            status = 409;
            message = "Такая запись уже существует или используется в других данных. Обновите список и проверьте введённые значения.";
        } else if (ex instanceof NoResourceFoundException) {
            status = 404;
            message = "Ресурс не найден";
        } else if (ex instanceof HttpRequestMethodNotSupportedException) {
            status = 405;
            message = "Метод не поддерживается";
        } else if (ex instanceof MissingServletRequestParameterException
                || ex
                instanceof
                MissingServletRequestPartException) {
            status = 400;
            message = "Отсутствует обязательный параметр запроса";
        } else if (ex instanceof AccessDeniedException) {
            status = 403;
            message = "Недостаточно прав";
        }
        if (status == 500) LOG.error("Request {} failed", request.getAttribute("correlationId"), ex);
        return ResponseEntity.status(status)
                .body(
                        new ErrorBody(
                                status,
                                "HTTP_" + status,
                                message,
                                String.valueOf(request.getAttribute("correlationId")),
                                fields));
    }

    private static String validationMessage(FieldError error) {
        return switch (Objects.toString(error.getCode(), "")) {
            case "NotBlank", "NotNull" -> "Заполните это поле.";
            case "Size" -> "Проверьте длину текста или количество выбранных значений.";
            case "Pattern" -> error.getField().endsWith("login")
                    ? "Логин: от 3 до 40 символов. Используйте латинские буквы, цифры, точку, дефис или подчёркивание."
                    : "Выберите допустимое значение.";
            case "DecimalMin", "DecimalMax", "Min", "Max" -> "Укажите число в допустимом диапазоне.";
            default -> "Проверьте значение этого поля.";
        };
    }
}
