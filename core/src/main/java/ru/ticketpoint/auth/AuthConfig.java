package ru.ticketpoint.auth;

import ru.ticketpoint.config.EnvironmentFile;
import java.nio.file.Path;
import java.util.Map;

public record AuthConfig(String login, String name, String password) {
    public static AuthConfig fromEnvironment() {
        Map<String, String> env = EnvironmentFile.read(Path.of(".env"), System.getenv());
        return new AuthConfig(env.getOrDefault("BOOTSTRAP_ADMIN_LOGIN", "admin"),
                env.getOrDefault("BOOTSTRAP_ADMIN_NAME", "Администратор"), env.get("BOOTSTRAP_ADMIN_PASSWORD"));
    }
    @Override public String toString() { return "AuthConfig[credentials=<hidden>]"; }
}
