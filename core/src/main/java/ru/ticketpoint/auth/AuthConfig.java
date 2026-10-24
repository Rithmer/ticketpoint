package ru.ticketpoint.auth;

import java.nio.file.Path;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import ru.ticketpoint.config.EnvironmentFile;

public record AuthConfig(String login, String name, String password) {
    public static AuthConfig fromEnvironment() {
        Map<String, String> env = EnvironmentFile.read(Path.of(".env"), System.getenv());
        return new AuthConfig(env.getOrDefault("BOOTSTRAP_ADMIN_LOGIN", "admin"),
                env.getOrDefault("BOOTSTRAP_ADMIN_NAME", "Администратор"), env.get("BOOTSTRAP_ADMIN_PASSWORD"));
    }

    @Override
    public @NotNull String toString() {
        return "AuthConfig[credentials=<hidden>]";
    }
}
