package ru.ticketpoint.auth;

public enum Role {
    ADMIN("Администратор"), MANAGER("Менеджер"), USER("Покупатель"), VIEWER("Наблюдатель");
    private final String label;

    Role(String label) {
        this.label = label;
    }

    public boolean canWrite() {
        return this == ADMIN || this == MANAGER;
    }

    @Override
    public String toString() {
        return label;
    }
}
