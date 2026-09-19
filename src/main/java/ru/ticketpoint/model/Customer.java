package ru.ticketpoint.model;

import java.util.regex.Pattern;

public final class Customer {
    private static final Pattern CODE = Pattern.compile(
            "^CUS-[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}$");
    private final Long id;
    private final String customerCode;
    private final String name;

    public Customer(Long id, String customerCode, String name) {
        this.id = Validation.optionalId(id);
        this.customerCode = normalizeCode(customerCode);
        this.name = Validation.text(name, "Имя", 120);
    }

    public static String normalizeCode(String code) {
        String normalized = Validation.text(code, "Код клиента", 40).toUpperCase(java.util.Locale.ROOT);
        if (!CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Код клиента должен иметь формат CUS-<UUID>");
        }
        return normalized;
    }

    public Long getId() { return id; }
    public String getCustomerCode() { return customerCode; }
    public String getName() { return name; }

    @Override
    public String toString() {
        return "%s: %s [%s]".formatted(getId(), getName(), customerCode);
    }
}
