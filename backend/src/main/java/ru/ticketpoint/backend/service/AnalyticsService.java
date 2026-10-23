package ru.ticketpoint.backend.service;

import java.math.*;
import java.util.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ticketpoint.backend.repository.BookingRepository;
import ru.ticketpoint.backend.security.CurrentUser;

@Service
public class AnalyticsService {
    private final BookingRepository bookings;
    private final CurrentUser current;

    public AnalyticsService(
            BookingRepository bookings, CurrentUser current) {
        this.bookings = bookings;
        this.current = current;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summary() {
        var user = current.optional().orElse(null);
        var all =
                bookings.findAll(
                        (r, q, cb) ->
                                user != null && user.getRole().equals("USER")
                                        ? (user.getCustomerId() == null
                                        ? cb.disjunction()
                                        : cb.equal(r.get("customer").get("id"), user.getCustomerId()))
                                        : cb.conjunction());
        long active = all.stream().filter(b -> !b.getArchived()).count();
        Map<String, Long> byEvent = new TreeMap<>(),
                byStatus = new TreeMap<>(),
                byPriority = new TreeMap<>();
        for (var b : all) {
            byEvent.merge(b.getEvent().getPublicId() + " | " + b.getEvent().getTitle(), 1L, Long::sum);
            byStatus.merge(b.getStatus().name(), 1L, Long::sum);
            byPriority.merge(
                    PriorityCalculator.calculate(b.getStatus(), b.getPrice(), b.getVip()).name(), 1L, Long::sum);
        }
        var avg =
                all.isEmpty()
                        ? BigDecimal.ZERO
                        : all.stream()
                        .map(ru.ticketpoint.backend.domain.Booking::getPrice)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(all.size()), 2, RoundingMode.HALF_UP);
        return Map.of(
                "total",
                all.size(),
                "active",
                active,
                "archived",
                all.size() - active,
                "vip",
                all.stream().filter(ru.ticketpoint.backend.domain.Booking::getVip).count(),
                "averagePrice",
                avg,
                "byEvent",
                byEvent,
                "byStatus",
                byStatus,
                "byPriority",
                byPriority);
    }
}
