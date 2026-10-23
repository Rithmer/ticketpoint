package ru.ticketpoint.fx;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;

final class UiOperations {
    private final FxTaskExecutor executor;
    private final Node workspace;
    private final Label status;
    private final ProgressIndicator progress;
    private long operationId;

    UiOperations(FxTaskExecutor executor, Node workspace, Label status, ProgressIndicator progress) {
        this.executor = executor;
        this.workspace = workspace;
        this.status = status;
        this.progress = progress;
    }

    <T> void run(Callable<T> action, String pending, Consumer<T> success) {
        long currentOperation = ++operationId;
        busy(true, pending);
        executor.execute(action, result -> {
            if (currentOperation != operationId) {
                return;
            }
            status.setText("Готово");
            try {
                success.accept(result);
            } finally {
                if (currentOperation == operationId) {
                    busy(false, status.getText());
                }
            }
        }, error -> {
            if (currentOperation == operationId) {
                busy(false, "Операция не выполнена.");
                UiErrors.show(workspace.getScene().getWindow(), error);
            }
        });
    }

    void message(String message) {
        status.setText(message);
    }

    private void busy(boolean value, String message) {
        workspace.setDisable(value);
        progress.setVisible(value);
        status.setText(message);
    }
}
