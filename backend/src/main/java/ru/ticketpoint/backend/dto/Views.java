package ru.ticketpoint.backend.dto;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import org.springframework.data.domain.Page;
import ru.ticketpoint.backend.domain.*;
import ru.ticketpoint.backend.service.PriorityCalculator;

public final class Views {
    private Views() {
    }

    public record PageView<T>(
            List<T> content,
            int number,
            int size,
            long totalElements,
            int totalPages,
            boolean first,
            boolean last) {
        public static <T> PageView<T> from(Page<T> page) {
            return new PageView<>(
                    page.getContent(),
                    page.getNumber(),
                    page.getSize(),
                    page.getTotalElements(),
                    page.getTotalPages(),
                    page.isFirst(),
                    page.isLast());
        }
    }

    public record UserView(
            UUID id,
            String login,
            String name,
            String role,
            boolean enabled,
            boolean passwordChangeRequired,
            long version) {
    }

    public static UserView user(UserAccount u) {
        return new UserView(
                u.getPublicId(), u.getLogin(), u.getName(), u.getRole(), u.getEnabled(), u.getPasswordChangeRequired(), u.getVersion());
    }

    public record CustomerView(Long id, String code, String name) {
    }

    public static CustomerView customer(Customer c) {
        return new CustomerView(c.getId(), c.getCode(), c.getName());
    }

    public record AuditView(
            UUID id,
            UUID actorId,
            String actor,
            String eventType,
            String entityType,
            UUID entityId,
            String summary,
            String correlationId,
            Instant occurredAt) {
    }

    public static AuditView audit(AuditEvent e) {
        return new AuditView(
                e.getId(),
                e.getActorId(),
                e.getActor(),
                e.getEventType(),
                e.getEntityType(),
                e.getEntityId(),
                e.getSummary(),
                e.getCorrelationId(),
                e.getOccurredAt());
    }

    public record EventView(
            UUID id,
            String title,
            String venue,
            LocalDateTime startsAt,
            BigDecimal price,
            boolean active,
            boolean available,
            long version) {
    }

    public static EventView event(Event e, LocalDateTime now) {
        return new EventView(e.getPublicId(), e.getTitle(), e.getVenue(), e.getStartsAt(), e.getPrice(), e.getActive(), e.getActive() && e.getStartsAt().isAfter(now), e.getVersion());
    }

    public record BookingView(
            UUID id,
            String ticketNumber,
            Long customerId,
            String customerName,
            UUID eventId,
            String eventTitle,
            BookingStatus status,
            BigDecimal price,
            boolean vip,
            String description,
            Set<String> tags,
            Priority priority,
            boolean archived,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            long version) {
    }

    public static BookingView booking(Booking b) {
        return booking(b, false);
    }

    public static BookingView booking(Booking b, boolean publicView) {
        return new BookingView(
                b.getPublicId(),
                b.getTicketNumber(),
                publicView ? null : b.getCustomer().getId(),
                publicView ? "Покупатель" : b.getCustomer().getName(),
                b.getEvent().getPublicId(),
                b.getEvent().getTitle(),
                b.getStatus(),
                b.getPrice(),
                b.getVip(),
                publicView ? null : b.getDescription(),
                publicView ? Set.of() : Set.copyOf(b.getTags()),
                PriorityCalculator.calculate(b.getStatus(), b.getPrice(), b.getVip()),
                b.getArchived(),
                b.getCreatedAt(),
                b.getUpdatedAt(),
                b.getVersion());
    }

    public record NoteView(
            UUID id,
            UUID authorId,
            String authorName,
            String body,
            boolean pinned,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            long version) {
    }

    public static NoteView note(BookingNote n) {
        return new NoteView(
                n.getPublicId(),
                n.getAuthor().getPublicId(),
                n.getAuthor().getName(),
                n.getBody(),
                n.getPinned(),
                n.getCreatedAt(),
                n.getUpdatedAt(),
                n.getVersion());
    }

    public record Tokens(
            String accessToken,
            Instant accessExpiresAt,
            String refreshToken,
            Instant refreshExpiresAt,
            UserView user) {
    }

    public record Failure(UUID id, String message) {
    }

    public record BulkResult(int requested, int succeeded, List<Failure> failures) {
    }
}
