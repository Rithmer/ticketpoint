package ru.ticketpoint.fx;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.stage.Window;
import ru.ticketpoint.model.Validation;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;

final class FormDialog {
    private final Dialog<ButtonType> dialog;
    private boolean completed;
    private final ButtonType save = new ButtonType("Сохранить", ButtonBar.ButtonData.OK_DONE);

    private FormDialog(Window owner, String title, String header, Parent content) throws IOException {
        dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getStylesheets().add(UiResources.stylesheet());
        dialog.getDialogPane().getButtonTypes().addAll(save, new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE));
    }

    static <C> void show(Window owner, String resource, String title, String header,
                         BiConsumer<C, FormDialog> configure) {
        try {
            FXMLLoader loader = new FXMLLoader(UiResources.location(resource));
            Parent content = loader.load();
            FormDialog form = new FormDialog(owner, title, header, content);
            configure.accept(loader.getController(), form);
            form.dialog.showAndWait();
        } catch (IOException error) {
            UiErrors.show(owner, error);
        }
    }

    void onSave(Runnable action) {
        dialog.getDialogPane().lookupButton(save).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            dialog.getDialogPane().lookupAll(".invalid").forEach(node -> node.getStyleClass().remove("invalid"));
            action.run();
        });
    }

    String text(TextField field, String label, int maxLength) {
        try {
            return Validation.text(field.getText(), label, maxLength);
        } catch (IllegalArgumentException error) {
            invalid(field);
            throw error;
        }
    }

    void invalid(Control field) {
        if (!field.getStyleClass().contains("invalid")) {
            field.getStyleClass().add("invalid");
        }
        field.requestFocus();
    }

    <T> void submit(FxTaskExecutor executor, Callable<T> action, Label feedback, Runnable saved) {
        dialog.getDialogPane().setDisable(true);
        feedback.setText("Сохранение…");
        executor.execute(action, result -> {
            dialog.getDialogPane().setDisable(false);
            close();
            saved.run();
        }, error -> {
            dialog.getDialogPane().setDisable(false);
            feedback.setText(UiErrors.message(error));
        });
    }

    void onCancel(Runnable action) {
        dialog.setOnHidden(event -> {
            if (!completed) action.run();
        });
    }

    void close() {
        completed = true;
        dialog.close();
    }
}
