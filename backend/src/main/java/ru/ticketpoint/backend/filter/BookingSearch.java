package ru.ticketpoint.backend.filter;

import jakarta.persistence.criteria.*;

import java.math.BigDecimal;
import java.util.*;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import ru.ticketpoint.backend.domain.*;
import ru.ticketpoint.backend.error.ApiException;

public final class BookingSearch {
    private BookingSearch() {
    }

    public record Filter(
            String q,
            UUID eventId,
            BookingStatus status,
            Boolean vip,
            Priority priority,
            String tag,
            Boolean archived,
            BigDecimal price,
            String comparison) {
    }

    public static Pageable page(
            int page, int size, String sort, String direction, Set<String> allowed) {
        if (page < 0 || size < 1 || size > 100)
            throw ApiException.bad("Выберите страницу и размер списка от 1 до 100 записей.");
        if (!allowed.contains(sort)) throw ApiException.bad("Недопустимое поле сортировки");
        Sort.Direction dir;
        try {
            dir = Sort.Direction.fromString(direction);
        } catch (IllegalArgumentException ex) {
            throw ApiException.bad("Выберите порядок сортировки: по возрастанию или по убыванию.");
        }
        return PageRequest.of(page, size, Sort.by(dir, sort).and(Sort.by("id")));
    }

    public static Specification<Booking> spec(Filter f) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (f.q() != null && !f.q().isBlank()) {
                if (f.q().length() > 200) throw ApiException.bad("Поиск: не более 200 символов");
                String q =
                        "%"
                                + f.q()
                                .toLowerCase(Locale.ROOT)
                                .replace("\\", "\\\\")
                                .replace("%", "\\%")
                                .replace("_", "\\_")
                                + "%";
                var customer = root.join("customer");
                var event = root.join("event");
                predicates.add(
                        cb.or(
                                cb.like(cb.lower(root.get("ticketNumber")), q, '\\'),
                                cb.like(cb.lower(customer.get("name")), q, '\\'),
                                cb.like(cb.lower(event.get("title")), q, '\\'),
                                cb.like(cb.lower(event.get("venue")), q, '\\'),
                                cb.like(cb.lower(root.get("description")), q, '\\')));
            }
            if (f.eventId() != null)
                predicates.add(cb.equal(root.get("event").get("publicId"), f.eventId()));
            if (f.status() != null) predicates.add(cb.equal(root.get("status"), f.status()));
            if (f.vip() != null) predicates.add(cb.equal(root.get("vip"), f.vip()));
            if (f.archived() != null) predicates.add(cb.equal(root.get("archived"), f.archived()));
            if (f.tag() != null && !f.tag().isBlank())
                predicates.add(cb.isMember(f.tag().trim().toLowerCase(Locale.ROOT), root.get("tags")));
            if (f.priority() != null) {
                Predicate pending = root.get("status").in(BookingStatus.NEW, BookingStatus.CONFIRMED);
                Predicate high = cb.and(pending, cb.isTrue(root.get("vip")));
                Predicate medium =
                        cb.and(
                                pending,
                                cb.isFalse(root.get("vip")),
                                cb.ge(root.get("price"), new BigDecimal("5000")));
                predicates.add(
                        switch (f.priority()) {
                            case HIGH -> high;
                            case MEDIUM -> medium;
                            case LOW -> cb.not(cb.or(high, medium));
                        });
            }
            if (f.price() != null) {
                String op = f.comparison() == null ? "eq" : f.comparison();
                predicates.add(
                        switch (op) {
                            case "eq" -> cb.equal(root.get("price"), f.price());
                            case "gt" -> cb.gt(root.get("price"), f.price());
                            case "gte" -> cb.ge(root.get("price"), f.price());
                            case "lt" -> cb.lt(root.get("price"), f.price());
                            case "lte" -> cb.le(root.get("price"), f.price());
                            default -> throw ApiException.bad("Выберите способ сравнения цены.");
                        });
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
