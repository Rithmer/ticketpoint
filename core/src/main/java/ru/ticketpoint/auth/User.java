package ru.ticketpoint.auth;

public record User(long id, String login, String name, Role role, boolean enabled,
                   boolean passwordChangeRequired, long version, Long customerId) {
    public User(long id, String login, String name, Role role, boolean enabled,
                boolean passwordChangeRequired, long version) {
        this(id, login, name, role, enabled, passwordChangeRequired, version, null);
    }
}
