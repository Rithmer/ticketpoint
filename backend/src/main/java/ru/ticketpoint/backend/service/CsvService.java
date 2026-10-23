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
                            b.ticketNumber(),
                            b.customerId(),
                            b.eventId(),
                            b.status(),
                            b.vip(),
                            safe(b.description()),
                            String.join(";", new TreeSet<>(b.tags())));
            }
            return writer.toString();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String safe(String value) {
        if (value == null) return "";
        return value.matches("^[=+@\\-].*") ? "'" + value : value;
    }

    public Preview preview(MultipartFile file, String mode) {
        if (!Set.of("NEW_ONLY", "UPSERT").contains(mode))
            throw ApiException.bad("Выберите режим импорта: только новые записи или создание и обновление.");
        if (file.isEmpty() || file.getSize() > 2 * 1024 * 1024)
            throw ApiException.bad("Размер CSV: 1 байт .. 2 МБ");
        previews.entrySet().removeIf(e -> e.getValue().expires().isBefore(Instant.now()));
        if (previews.size() >= 100)
            throw new ApiException(429, "Слишком много проверок CSV, повторите позже");
        var rows = new ArrayList<Row>();
        var errors = new ArrayList<RowError>();
        var codes = new HashSet<String>();
        var all =
                bookings.export(
                        new BookingSearch.Filter(null, null, null, null, null, null, null, null, null),
                        Sort.by("ticketNumber"));
        var existing =
                all.stream()
                        .collect(
                                Collectors.toMap(
                                        b -> b.ticketNumber().toUpperCase(Locale.ROOT), b -> b));
        try (var reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             var parser =
                     CSVFormat.DEFAULT
                             .builder()
                             .setHeader()
                             .setSkipHeaderRecord(true)
                             .setIgnoreEmptyLines(true)
                             .get()
                             .parse(reader)) {
            if (!parser.getHeaderNames().equals(List.of(HEADERS)))
                throw ApiException.bad("Столбцы CSV: " + String.join(",", HEADERS));
            for (var record : parser) {
                long line = record.getRecordNumber() + 1;
                if (line > 5001) throw ApiException.bad("Не более 5000 строк CSV");
                try {
                    if (record.size() != HEADERS.length)
                        throw ApiException.bad("Неверное количество столбцов");
                    String code = record.get("ticketNumber").trim().toUpperCase(Locale.ROOT);
                    if (!code.matches("[A-Z0-9][A-Z0-9-]{0,39}"))
                        throw ApiException.bad("Неверный номер билета");
                    if (!codes.add(code)) throw ApiException.bad("Повтор номера в файле");
                    var old = existing.get(code);
                    if (old != null && mode.equals("NEW_ONLY"))
                        throw ApiException.bad("Билет уже существует");
                    var status = BookingStatus.valueOf(record.get("status"));
                    String vip = record.get("vip");
                    if (!Set.of("true", "false").contains(vip))
                        throw ApiException.bad("В столбце VIP укажите true (да) или false (нет).");
                    Set<String> tags =
                            record.get("tags").isBlank()
                                    ? Set.of()
                                    : new LinkedHashSet<>(Arrays.asList(record.get("tags").split(";", -1)));
                    var input =
                            new Requests.BookingInput(
                                    code,
                                    Long.valueOf(record.get("customerId")),
                                    UUID.fromString(record.get("eventId")),
                                    status,
                                    Boolean.parseBoolean(vip),
                                    record.get("description"),
                                    tags,
                                    old == null ? null : old.version());
                    var violations = validator.validate(input);
                    if (!violations.isEmpty())
                        throw ApiException.bad(
                                "Проверьте заполнение обязательных полей и длину текста в этой строке.");
                    bookings.validateImport(old == null ? null : old.id(), input);
                    rows.add(new Row(line, input, old == null ? null : old.id()));
                } catch (ApiException | IllegalArgumentException ex) {
                    errors.add(
                            new RowError(
                                    line,
                                    ex instanceof ApiException
                                            ? ex.getMessage()
                                            : "Проверьте идентификатор мероприятия, номер клиента и статус в этой строке."));
                }
            }
        } catch (IOException | UncheckedIOException ex) {
            throw ApiException.bad("Не удалось прочитать CSV");
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
            throw ApiException.bad("Проверка не найдена или устарела. Проверьте CSV заново");
        if (!checked.mode().equals(req.mode()))
            throw ApiException.bad("Режим должен совпадать с предварительной проверкой");
        if (!previews.remove(req.previewId(), checked)) throw ApiException.bad("Импорт уже применён");
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
                        new RowError(row.number(), "Данные изменены после проверки. Проверьте CSV заново"));
            }
        return new ImportResult(ok, errors);
    }
}
