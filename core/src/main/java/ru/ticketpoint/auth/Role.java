package ru.ticketpoint.auth;

public enum Role {
    ADMIN("Администратор"), MANAGER("Менеджер"), USER("Пользователь");
    private final String label;
    Role(String label) { this.label = label; }
    public boolean canWrite() { return this != USER; }
    @Override public String toString() { return label; }
}
