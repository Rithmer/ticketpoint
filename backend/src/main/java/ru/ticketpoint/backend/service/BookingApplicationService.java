package ru.ticketpoint.backend.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ticketpoint.backend.domain.*;
import ru.ticketpoint.backend.dto.*;
import ru.ticketpoint.backend.error.ApiException;
import ru.ticketpoint.backend.filter.BookingSearch;
import ru.ticketpoint.backend.repository.*;
import ru.ticketpoint.backend.security.CurrentUser;

@Service
public class BookingApplicationService {
    private static final int MAX_EXPORT_ROWS = 10_000;
    private final Clock clock;
    private final BookingRepository bookings;
    private final EventRepository events;
    private final CustomerRepository customers;
    private final BookingNoteRepository notes;
    private final AuditService audit;
    private final CurrentUser current;

    public BookingApplicationService(
            BookingRepository bookings,
            EventRepository events,
            CustomerRepository customers,
            BookingNoteRepository notes,
            AuditService audit,
            CurrentUser current, Clock clock) {
        this.clock = clock;
        this.bookings = bookings;
        this.events = events;
        this.customers = customers;
        this.notes = notes;
        this.audit = audit;
        this.current = current;
    }

    @Transactional(readOnly = true)
    public Page<Views.BookingView> search(BookingSearch.Filter filter, Pageable page) {
        var user = current.optional().orElse(null);
        return bookings.findAll(scope(filter, user), page).map(b -> Views.booking(b, user == null));
    }

    @Transactional(readOnly = true)
    public List<Views.BookingView> export(BookingSearch.Filter filter, Sort sort) {
        var user = current.optional().orElse(null);
        var page = bookings.findAll(scope(filter, user), PageRequest.of(0, MAX_EXPORT_ROWS + 1, sort));
        if (page.getNumberOfElements() > MAX_EXPORT_ROWS)
            throw ApiException.bad("Выгрузка ограничена 10 000 строками. Уточните фильтры и повторите попытку.");
        return page.getContent().stream().map(b -> Views.booking(b, user == null)).toList();
    }

    @Transactional(readOnly = true)
    public Views.BookingView get(UUID id) {
        return Views.booking(require(id), current.optional().isEmpty());
    }

    public Booking require(UUID id) {
        var b = bookings.findByPublicId(id).orElseThrow(ApiException::missing);
        var u = current.optional().orElse(null);
        if (u == null && b.getArchived()) throw ApiException.missing();
        if (u != null && u.getRole().equals("USER") && !Objects.equals(u.getCustomerId(), b.getCustomer().getId()))
            throw ApiException.missing();
        return b;
    }

    private Specification<Booking> scope(BookingSearch.Filter filter, ru.ticketpoint.backend.domain.UserAccount u) {
        Specification<Booking> spec = BookingSearch.spec(filter, u != null);
        if (u == null) spec = spec.and((r, q, cb) -> cb.isFalse(r.get("archived")));
        if (u != null && u.getRole().equals("USER"))
            spec =
                    spec.and(
                            (r, q, cb) ->
                                    u.getCustomerId() == null
                                            ? cb.disjunction()
                                            : cb.equal(r.get("customer").get("id"), u.getCustomerId()));
        return spec;
    }

    @Transactional(readOnly = true)
    public List<Booking> findExistingByTicketNumbers(Collection<String> ticketNumbers) {
        if (ticketNumbers.isEmpty()) return List.of();
        var normalized = ticketNumbers.stream().map(s -> s.toUpperCase(Locale.ROOT)).toList();
        return bookings.findByTicketNumbersIgnoreCase(normalized);
    }

    @Transactional
    public Views.BookingView create(Requests.BookingInput input) {
        requireCustomer(input.customerId());
        var b = new Booking();
        apply(b, input, true);
        bookings.saveAndFlush(b);
        audit.record("BOOKING_CREATED", "BOOKING", b.getPublicId(), "Создан билет " + b.getTicketNumber());
        return Views.booking(b);
    }

    @Transactional
    public Views.BookingView update(UUID id, Requests.BookingInput input) {
        var b = require(id);
        apply(b, input, false);
        bookings.flush();
        audit.record("BOOKING_UPDATED", "BOOKING", id, "Изменён билет " + b.getTicketNumber());
        return Views.booking(b);
    }

    @Transactional
    public Views.BookingView archive(UUID id) {
        var b = require(id);
        if (current.get().getRole().equals("USER") && b.getStatus() != BookingStatus.NEW)
            throw new ApiException(403, "Убрать в архив можно только свою новую заявку.");
        b.setArchived(true);
        bookings.flush();
        audit.record("BOOKING_ARCHIVED", "BOOKING", id, "Билет перемещён в архив");
        return Views.booking(b);
    }

    @Transactional
    public Views.BookingView restore(UUID id) {
        var b = require(id);
        b.setArchived(false);
        bookings.flush();
        audit.record("BOOKING_RESTORED", "BOOKING", id, "Билет восстановлен");
        return Views.booking(b);
    }

    @Transactional
    public void permanent(UUID id, String confirmation) {
        if (!"PERMANENTLY_DELETE".equals(confirmation))
            throw ApiException.bad("Подтвердите окончательное удаление билета.");
        var b = require(id);
        if (!b.getArchived()) throw ApiException.bad("Окончательно удалить можно только архивную запись");
        notes.deleteByBooking(b);
        bookings.delete(b);
        bookings.flush();
        audit.record("BOOKING_DELETED", "BOOKING", id, "Билет окончательно удалён");
    }

    @Transactional
    public void bulkOne(UUID id, Requests.Bulk req) {
        var b = require(id);
        switch (req.action()) {
            case ARCHIVE -> b.setArchived(true);
            case RESTORE -> b.setArchived(false);
            case SET_STATUS -> {
                if (b.getArchived()) throw ApiException.bad("Запись в архиве");
                validateStatusChange(b, req.status());
                b.setStatus(req.status());
            }
            case ADD_TAG -> {
                if (b.getTags().size() >= 20 && !b.getTags().contains(tag(req.tag())))
                    throw ApiException.bad("Можно добавить не больше 20 меток.");
                b.addTag(tag(req.tag()));
                b.setUpdatedAt(LocalDateTime.now(clock));
            }
            case REMOVE_TAG -> {
                b.removeTag(tag(req.tag()));
                b.setUpdatedAt(LocalDateTime.now(clock));
            }
        }
        bookings.flush();
        audit.record("BOOKING_BULK_" + req.action(), "BOOKING", id, "Массовая операция");
    }

    private void requireCustomer(Long customerId) {
        var user = current.get();
        if (user.getRole().equals("USER") && !Objects.equals(user.getCustomerId(), customerId))
            throw new ApiException(403, "Забронировать билет можно только на своё имя.");
    }

    private void authorizeBuyerChange(Booking booking, Requests.BookingInput input) {
        if (!current.get().getRole().equals("USER")) return;
        boolean editable = booking.getStatus() == BookingStatus.NEW && input.status() == BookingStatus.NEW;
        boolean cancellable = (booking.getStatus() == BookingStatus.NEW || booking.getStatus() == BookingStatus.CONFIRMED)
                && input.status() == BookingStatus.CANCELLED;
        if ((!editable && !cancellable) || !booking.getEvent().getStartsAt().isAfter(LocalDateTime.now(clock)))
            throw new ApiException(403, "Можно изменить новую заявку или отменить неоплаченную до начала мероприятия. Подтверждение и оплата доступны менеджеру.");
        if (booking.getVip() != input.vip() || !booking.getTags().equals(input.tags() == null ? Set.of() : input.tags()))
            throw new ApiException(403, "Условия обслуживания и служебные метки изменяет менеджер.");
    }

    public static String tag(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 50)
            throw ApiException.bad("Метка должна содержать от 1 до 50 символов.");
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public static void transition(BookingStatus from, BookingStatus to) {
        if (to == null) throw ApiException.bad("Выберите статус бронирования.");
        if (from == to) return;
        boolean valid =
                switch (from) {
                    case NEW -> to == BookingStatus.CONFIRMED || to == BookingStatus.CANCELLED;
                    case CONFIRMED -> to == BookingStatus.PAID || to == BookingStatus.CANCELLED;
                    case PAID -> to == BookingStatus.CANCELLED;
                    case CANCELLED -> false;
                };
        if (!valid)
            throw ApiException.bad("Этот статус сейчас недоступен. Обновите заявку и выберите подходящее действие.");
    }

    @Transactional(readOnly = true)
    public void validateImport(UUID id, Requests.BookingInput input) {
        prepare(id == null ? new Booking() : require(id), input, id == null);
    }

    private void validateStatusChange(Booking booking, BookingStatus status) {
        transition(booking.getStatus(), status);
        if ((status == BookingStatus.CONFIRMED || status == BookingStatus.PAID)
                && !booking.getEvent().getStartsAt().isAfter(LocalDateTime.now(clock)))
            throw ApiException.bad("Мероприятие уже началось. Подтвердить или оплатить билет нельзя.");
    }

    private record PreparedBooking(Event event, Customer customer, String code, Set<String> tags) {
    }

    private PreparedBooking prepare(Booking b, Requests.BookingInput input, boolean create) {
        requireCustomer(input.customerId());
        if (!create) {
            authorizeBuyerChange(b, input);
            ApiException.version(input.version(), b.getVersion());
            if (b.getArchived()) throw ApiException.bad("Сначала восстановите запись из архива");
        }
        var e =
                events
                        .findByPublicId(input.eventId())
                        .orElseThrow(() -> ApiException.bad("Мероприятие не найдено"));
        var c =
                customers
                        .findById(input.customerId())
                        .orElseThrow(() -> ApiException.bad("Клиент не найден"));
        boolean changed = create || !e.getId().equals(b.getEvent().getId()) || !c.getId().equals(b.getCustomer().getId());
        if (changed && (!e.getActive() || !e.getStartsAt().isAfter(LocalDateTime.now(clock))))
            throw ApiException.bad("Выберите активное будущее мероприятие");
        if (create && current.get().getRole().equals("USER") && (input.vip() || input.tags() != null && !input.tags().isEmpty()))
            throw new ApiException(403, "Условия обслуживания и служебные метки назначает менеджер.");
        if (create && input.status() != BookingStatus.NEW)
            throw ApiException.bad("Новая заявка создаётся в статусе «Новое».");
        if (!create) {
            validateStatusChange(b, input.status());
            if (changed && b.getStatus() != BookingStatus.NEW)
                throw ApiException.bad("Реквизиты меняются только у новой заявки");
        }
        String code =
                input.ticketNumber() == null || input.ticketNumber().isBlank()
                        ? (create
                        ? "TP-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT)
                        : b.getTicketNumber())
                        : input.ticketNumber().trim().toUpperCase(Locale.ROOT);
        if (code.length() > 40 || !code.matches("[A-Z0-9][A-Z0-9-]{0,39}"))
            throw ApiException.bad("Номер билета: до 40 латинских букв, цифр и дефисов");
        var duplicate = bookings.findByTicketNumberIgnoreCase(code).orElse(null);
        if (duplicate != null && !duplicate.getPublicId().equals(b.getPublicId()))
            throw new ApiException(409, "Номер билета уже существует");
        if (!create && !code.equals(b.getTicketNumber()))
            throw ApiException.bad("Номер выпущенного билета не меняется");
        var tags = new LinkedHashSet<String>();
        if (input.tags() != null) input.tags().forEach(t -> tags.add(tag(t)));
        if (tags.size() > 20) throw ApiException.bad("Можно добавить не больше 20 меток.");
        return new PreparedBooking(e, c, code, tags);
    }

    private void apply(Booking b, Requests.BookingInput input, boolean create) {
        var prepared = prepare(b, input, create);
        var e = prepared.event();
        var c = prepared.customer();
        String code = prepared.code();
        b.setTicketNumber(code);
        b.setCustomer(c);
        if (create || !e.getId().equals(b.getEvent().getId())) b.setPrice(e.getPrice());
        b.setEvent(e);
        b.setStatus(input.status());
        b.setVip(input.vip());
        b.setDescription(input.description() == null ? null : input.description().trim());
        b.setTags(prepared.tags());
        b.setUpdatedAt(LocalDateTime.now(clock));
    }
}
