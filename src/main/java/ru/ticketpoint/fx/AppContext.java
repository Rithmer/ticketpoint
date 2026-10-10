package ru.ticketpoint.fx;

import ru.ticketpoint.config.ApplicationServices;
import java.util.Objects;

public record AppContext(ApplicationServices services, FxTaskExecutor tasks) {
    public AppContext {
        Objects.requireNonNull(services);
        Objects.requireNonNull(tasks);
    }
}
