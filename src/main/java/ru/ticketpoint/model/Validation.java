package ru.ticketpoint.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Validation {
    private Validation() { }

    public static String text(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + ": значение обязательно");
        }
        String normalized = value.strip();
        if (normalized.codePointCount(0, normalized.length()) > maxLength) {
            throw new IllegalArgumentException(label + ": максимум " + maxLength + " символов");
        }
        if (normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(label + ": управляющие символы запрещены");
        }
        return normalized;
    }

    public static long id(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("ID должен быть положительным числом");
        }
        return id;
    }

    public static Long optionalId(Long id) {
        return id == null ? null : id(id);
    }

    public static BigDecimal price(BigDecimal price) {
        if (price == null || price.signum() < 0 || price.compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw new IllegalArgumentException("Цена должна быть от 0 до 9999999999.99");
        }
        try {
            return price.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Цена должна содержать не более двух знаков после запятой", e);
        }
    }
}
