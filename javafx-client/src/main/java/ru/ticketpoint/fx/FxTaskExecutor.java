package ru.ticketpoint.fx;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import javafx.concurrent.Task;
import ru.ticketpoint.auth.AuthException;

public final class FxTaskExecutor implements AutoCloseable {
    private Runnable sessionInvalid = () -> {
    };

    void onSessionInvalid(Runnable action) {
        sessionInvalid = Objects.requireNonNull(action);
    }

    private Runnable beforeOperation = () -> {
    };

    void beforeOperation(Runnable check) {
        beforeOperation = Objects.requireNonNull(check);
    }

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ticketpoint-io");
        thread.setDaemon(true);
        return thread;
    });

    public <T> void execute(Callable<T> action, Consumer<T> success, Consumer<Throwable> failure) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                beforeOperation.run();
                return action.call();
            }
        };
        task.setOnSucceeded(event -> success.accept(task.getValue()));
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            failure.accept(error);
            if (error instanceof AuthException auth && !auth.forbidden()
                    && (auth.getMessage().startsWith("Сеанс") || auth.getMessage().startsWith("Войдите"))) {
                sessionInvalid.run();
            }
        });
        worker.execute(task);
    }

    @Override
    public void close() {
        worker.shutdownNow();
    }
}
