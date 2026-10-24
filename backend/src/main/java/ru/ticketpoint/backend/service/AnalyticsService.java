package ru.ticketpoint.backend.service;

import java.math.*;
import java.util.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ticketpoint.backend.domain.BookingStatus;
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
        long ownerId = user == null || !user.getRole().equals("USER")
                ? 0
                : user.getCustomerId() == null ? -1 : user.getCustomerId();
        Object[] totals = bookings.analyticsTotals(ownerId).getFirst();
        long total = ((Number) totals[0]).longValue();
        long active = ((Number) totals[1]).longValue();
        long archived = ((Number) totals[2]).longValue();
        long vip = ((Number) totals[3]).longValue();
        BigDecimal sum = (BigDecimal) totals[4];
        BigDecimal avg = total == 0
                ? BigDecimal.ZERO
                : sum.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);

        Map<String, Long> byEvent = new TreeMap<>(), byStatus = new TreeMap<>(), byPriority = new TreeMap<>();
        for (Object[] row : bookings.analyticsByEvent(ownerId))
            byEvent.put(row[0] + " | " + row[1], ((Number) row[2]).longValue());
        for (Object[] row : bookings.analyticsByStatus(ownerId))
            byStatus.put(row[0].toString(), ((Number) row[1]).longValue());
        var pending = List.of(BookingStatus.NEW, BookingStatus.CONFIRMED);
        long high = bookings.countHighPriority(ownerId, pending);
        long medium = bookings.countMediumPriority(ownerId, pending);
        byPriority.put("HIGH", high);
        byPriority.put("MEDIUM", medium);
        byPriority.put("LOW", total - high - medium);
        return Map.of(
                "total",
                total,
                "active",
                active,
                "archived",
                archived,
                "vip",
                vip,
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
