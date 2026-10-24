package ru.ticketpoint.backend.service;

import jakarta.validation.Validator;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.commons.csv.*;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.ticketpoint.backend.domain.BookingStatus;
import ru.ticketpoint.backend.dto.*;
import ru.ticketpoint.backend.error.ApiException;
import ru.ticketpoint.backend.filter.BookingSearch;
import ru.ticketpoint.backend.security.CurrentUser;

@Service
public class CsvService {
    private static final String[] HEADERS = {
            "Номер билета", "Номер клиента", "Код мероприятия", "Статус", "VIP", "Описание", "Метки"
    };
    private static final String[] LEGACY_HEADERS = {
            "ticketNumber", "customerId", "eventId", "status", "vip", "description", "tags"
    };
    private final BookingApplicationService bookings;
    private final CurrentUser current;
    private final Validator validator;
    private final Map<UUID, Checked> previews = new ConcurrentHashMap<>();

    public record RowError(long row, String message) {
    }

    public record Preview(
            UUID previewId,
            String mode,
            int validRows,
            int invalidRows,
            List<RowError> errors,
            Instant expiresAt) {
    }

    public record ImportResult(int succeeded, List<RowError> errors) {
    }

    private record Row(long number, Requests.BookingInput input, UUID existing) {
    }

    private record Checked(
            UUID owner, String mode, List<Row> rows, List<RowError> errors, Instant expires) {
    }

    public CsvService(
            BookingApplicationService bookings,
            CurrentUser current,
            Validator validator) {
        this.bookings = bookings;
        this.current = current;
        this.validator = validator;
    }

    public String export(BookingSearch.Filter filter, Sort sort) {
        try {
            var writer = new StringWriter();
            try (var printer =
                         new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader(HEADERS).get())) {
                for (var b : bookings.export(filter, sort))
                    printer.printRecord(
                            safe(b.ticketNumber()),
                            b.customerId(),
                            b.eventId(),
                            statusName(b.status()),
                            b.vip() ? "Да" : "Нет",
                            safe(b.description()),
                            String.join(";", new TreeSet<>(b.tags()).stream().map(CsvService::safe).toList()));
            }
            return writer.toString();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String safe(String value) {
        if (value == null) return "";
        int first = 0;
        while (first < value.length()
                && (Character.isWhitespace(value.charAt(first)) || value.charAt(first) == '\uFEFF')) first++;
        if (first < value.length() && "=+-@".indexOf(value.charAt(first)) >= 0) return "'" + value;
        if (!value.isEmpty() && "\t\r\n".indexOf(value.charAt(0)) >= 0) return "'" + value;
        return value;
    }

    public Preview preview(MultipartFile file, String mode) {
        if (!Set.of("NEW_ONLY", "UPSERT").contains(mode))
            throw ApiException.bad("Выберите режим импорта: только новые записи или создание и обновление.");
        if (file.isEmpty() || file.getSize() > 2 * 1024 * 1024)
            throw ApiException.bad("Файл таблицы должен содержать от 1 байта до 2 МБ.");
        previews.entrySet().removeIf(e -> e.getValue().expires().isBefore(Instant.now()));
        if (previews.size() >= 100)
            throw new ApiException(429, "Проверка выполняется слишком часто. Подождите немного и повторите попытку.");
        var rows = new ArrayList<Row>();
        var errors = new ArrayList<RowError>();
        var codes = new HashSet<String>();
        try (var reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             var parser =
                     CSVFormat.DEFAULT
                             .builder()
                             .setHeader()
                             .setSkipHeaderRecord(true)
                             .setIgnoreEmptyLines(true)
                             .get()
                             .parse(reader)) {
            List<String> headers = parser.getHeaderNames();
            boolean legacyHeaders = headers.equals(List.of(LEGACY_HEADERS));
            if (!headers.equals(List.of(HEADERS)) && !legacyHeaders)
                throw ApiException.bad("Проверьте названия столбцов. Нужны: " + String.join(", ", HEADERS));
            var csvRows = new ArrayList<CSVRecord>();
            for (var record : parser) {
                if (record.getRecordNumber() + 1 > 5001)
                    throw ApiException.bad("В таблице должно быть не больше 5000 строк.");
                csvRows.add(record);
            }
            var codesToFind = new HashSet<String>();
            for (var record : csvRows) {
                if (record.size() != HEADERS.length) continue;
                try {
                    String code = value(record, legacyHeaders, "ticketNumber", "Номер билета")
                            .trim().toUpperCase(Locale.ROOT);
                    if (code.matches("[A-Z0-9][A-Z0-9-]{0,39}")) codesToFind.add(code);
                } catch (IllegalArgumentException ignored) {
                }
            }
            var existing = bookings.findExistingByTicketNumbers(codesToFind).stream()
                    .collect(Collectors.toMap(b -> b.getTicketNumber().toUpperCase(Locale.ROOT), b -> b));
            for (var record : csvRows) {
                long line = record.getRecordNumber() + 1;
                try {
                    if (record.size() != HEADERS.length)
                        throw ApiException.bad("Проверьте количество значений в этой строке.");
                    String code = value(record, legacyHeaders, "ticketNumber", "Номер билета").trim().toUpperCase(Locale.ROOT);
                    if (!code.matches("[A-Z0-9][A-Z0-9-]{0,39}"))
                        throw ApiException.bad("Укажите корректный номер билета.");
                    if (!codes.add(code)) throw ApiException.bad("Номер билета повторяется в файле.");
                    var old = existing.get(code);
                    if (old != null && mode.equals("NEW_ONLY"))
                        throw ApiException.bad("Билет с таким номером уже существует.");
                    var status = parseStatus(value(record, legacyHeaders, "status", "Статус"));
                    String vip = value(record, legacyHeaders, "vip", "VIP").trim();
                    if (!Set.of("true", "false", "да", "нет").contains(vip.toLowerCase(Locale.ROOT)))
                        throw ApiException.bad("В столбце «VIP» укажите «Да» или «Нет».");
                    Set<String> tags =
                            value(record, legacyHeaders, "tags", "Метки").isBlank()
                                    ? Set.of()
                                    : new LinkedHashSet<>(Arrays.asList(value(record, legacyHeaders, "tags", "Метки").split(";", -1)));
                    var input =
                            new Requests.BookingInput(
                                    code,
                                    Long.valueOf(value(record, legacyHeaders, "customerId", "Номер клиента")),
                                    UUID.fromString(value(record, legacyHeaders, "eventId", "Код мероприятия")),
                                    status,
                                    vip.equalsIgnoreCase("true") || vip.equalsIgnoreCase("да"),
                                    value(record, legacyHeaders, "description", "Описание"),
                                    tags,
                                    old == null ? null : old.getVersion());
                    var violations = validator.validate(input);
                    if (!violations.isEmpty())
                        throw ApiException.bad(
                                "Проверьте заполнение обязательных полей и длину текста в этой строке.");
                    bookings.validateImport(old == null ? null : old.getPublicId(), input);
                    rows.add(new Row(line, input, old == null ? null : old.getPublicId()));
                } catch (ApiException | IllegalArgumentException ex) {
                    errors.add(
                            new RowError(
                                    line,
                                    ex instanceof ApiException
                                            ? ex.getMessage()
                                            : "Проверьте код мероприятия, номер клиента и статус в этой строке."));
                }
            }
        } catch (IOException | UncheckedIOException ex) {
            throw ApiException.bad("Не удалось прочитать файл. Убедитесь, что это таблица в формате .csv.");
        }
        UUID id = UUID.randomUUID();
        Instant expiry = Instant.now().plusSeconds(900);
        previews.put(
                id,
                new Checked(current.get().getPublicId(), mode, List.copyOf(rows), List.copyOf(errors), expiry));
        return new Preview(id, mode, rows.size(), errors.size(), errors, expiry);
    }

    public ImportResult apply(Requests.ApplyImport req) {
        var checked = previews.get(req.previewId());
        if (checked == null
                || !checked.owner().equals(current.get().getPublicId())
                || checked.expires().isBefore(Instant.now()))
            throw ApiException.bad("Результат проверки устарел. Проверьте файл заново.");
        if (!checked.mode().equals(req.mode()))
            throw ApiException.bad("Выберите тот же способ загрузки, который использовался при проверке.");
        if (!previews.remove(req.previewId(), checked)) throw ApiException.bad("Эта загрузка уже выполнена.");
        int ok = 0;
        var errors = new ArrayList<>(checked.errors());
        for (var row : checked.rows())
            try {
                if (row.existing() == null) bookings.create(row.input());
                else bookings.update(row.existing(), row.input());
                ok++;
            } catch (ApiException ex) {
                errors.add(new RowError(row.number(), ex.getMessage()));
            } catch (DataAccessException ex) {
                errors.add(
                        new RowError(row.number(), "Данные изменились после проверки. Проверьте файл заново."));
            }
        return new ImportResult(ok, errors);
    }

    private static String value(CSVRecord record, boolean legacyHeaders, String legacy, String localized) {
        return record.get(legacyHeaders ? legacy : localized);
    }

    private static BookingStatus parseStatus(String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "новое", "новая заявка", "new" -> BookingStatus.NEW;
            case "подтверждено", "confirmed" -> BookingStatus.CONFIRMED;
            case "оплачено", "paid" -> BookingStatus.PAID;
            case "отменено", "cancelled" -> BookingStatus.CANCELLED;
            default -> throw new IllegalArgumentException("Неизвестный статус");
        };
    }

    private static String statusName(BookingStatus status) {
        return switch (status) {
            case NEW -> "Новое";
            case CONFIRMED -> "Подтверждено";
            case PAID -> "Оплачено";
            case CANCELLED -> "Отменено";
        };
    }
}
