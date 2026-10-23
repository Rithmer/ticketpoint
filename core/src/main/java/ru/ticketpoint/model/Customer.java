package ru.ticketpoint.model;

import java.util.Locale;
import java.util.regex.Pattern;

public record Customer(Long id, String customerCode, String name) {
    private static final Pattern CODE = Pattern.compile(
            "^CUS-[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}$");

    public Customer {
        id = Validation.optionalId(id);
        customerCode = normalizeCode(customerCode);
        name = Validation.text(name, "Имя", 120);
    }

    public static String normalizeCode(String code) {
        String normalized = Validation.text(code, "Код клиента", 40).toUpperCase(Locale.ROOT);
        if (!CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Код клиента должен иметь формат CUS-<UUID>");
        }
        return normalized;
    }
}
