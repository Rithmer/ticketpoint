package ru.ticketpoint.fx;

import javafx.concurrent.Task;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class FxTaskExecutor implements AutoCloseable {
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ticketpoint-io");
        thread.setDaemon(true);
        return thread;
    });

    public <T> void execute(Callable<T> action, Consumer<T> success, Consumer<Throwable> failure) {
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception { return action.call(); }
        };
        task.setOnSucceeded(event -> success.accept(task.getValue()));
        task.setOnFailed(event -> failure.accept(task.getException()));
        worker.execute(task);
    }

    @Override public void close() { worker.shutdownNow(); }
}
