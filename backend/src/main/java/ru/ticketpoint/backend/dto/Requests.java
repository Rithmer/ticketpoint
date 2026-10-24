package ru.ticketpoint.backend.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import ru.ticketpoint.backend.domain.BookingStatus;

public final class Requests {
    private Requests() {
    }

    public record Login(
            @NotBlank @Size(max = 40) String login, @NotBlank @Size(max = 128) String password) {
        @JsonCreator
        public Login {
        }
    }

    public record Refresh(@NotBlank @Size(max = 512) String refreshToken) {
        @JsonCreator
        public Refresh {
        }
    }

    public record Registration(
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9][a-zA-Z0-9_.-]{2,39}") String login,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(min = 8, max = 128) String password,
            @NotBlank @Size(max = 128) String confirmation) {
        @JsonCreator
        public Registration {
        }
    }

    public record Password(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(max = 128) String newPassword) {
        @JsonCreator
        public Password {
        }
    }

    public record BookingInput(
            @Size(max = 40) String ticketNumber,
            @NotNull Long customerId,
            @NotNull UUID eventId,
            @NotNull BookingStatus status,
            boolean vip,
            @Size(max = 1000) String description,
            @Size(max = 20) Set<@NotBlank @Size(max = 50) String> tags,
            Long version) {
        @JsonCreator
        public BookingInput {
        }
    }

    public record EventInput(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 200) String venue,
            @NotNull LocalDateTime startsAt,
            @NotNull @DecimalMin("0") @DecimalMax("9999999999.99") BigDecimal price,
            boolean active,
            Long version) {
        @JsonCreator
        public EventInput {
        }
    }

    public record CustomerInput(@NotBlank @Size(max = 120) String name) {
        @JsonCreator
        public CustomerInput {
        }
    }

    public record NoteInput(@NotBlank @Size(max = 4000) String body, boolean pinned, Long version) {
        @JsonCreator
        public NoteInput {
        }
    }

    public enum BulkAction {
        ARCHIVE,
        RESTORE,
        SET_STATUS,
        ADD_TAG,
        REMOVE_TAG
    }

    public record Bulk(
            @NotNull @Size(min = 1, max = 500) Set<@NotNull UUID> ids,
            @NotNull BulkAction action,
            BookingStatus status,
            @Size(max = 50) String tag) {
        @JsonCreator
        public Bulk {
        }
    }

    public record UserInput(
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9][a-zA-Z0-9_.-]{2,39}") String login,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Pattern(regexp = "ADMIN|MANAGER|USER|VIEWER") String role,
            boolean enabled,
            Long version) {
        @JsonCreator
        public UserInput {
        }
    }

    public record NewUser(
            @NotNull @Valid UserInput user,
            @NotBlank @Size(max = 128) String temporaryPassword) {
        @JsonCreator
        public NewUser {
        }
    }

    public record ResetPassword(@NotBlank @Size(max = 128) String temporaryPassword) {
        @JsonCreator
        public ResetPassword {
        }
    }

    public record ApplyImport(
            @NotNull UUID previewId, @NotBlank @Pattern(regexp = "NEW_ONLY|UPSERT") String mode) {
        @JsonCreator
        public ApplyImport {
        }
    }
}
