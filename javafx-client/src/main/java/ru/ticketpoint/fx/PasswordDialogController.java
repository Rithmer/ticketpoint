package ru.ticketpoint.fx;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Window;
import ru.ticketpoint.auth.PasswordHasher;

public final class PasswordDialogController {
    @FXML
    private PasswordField currentPassword;
    @FXML
    private PasswordField newPassword;
    @FXML
    private PasswordField confirmation;
    @FXML
    private Label feedback;
    private FormDialog form;
    private AppContext context;
    private Runnable saved;

    static void show(Window owner, AppContext context, Runnable saved, Runnable cancelled) {
        FormDialog.<PasswordDialogController>show(owner, "password-dialog.fxml", "Смена пароля",
                context.user().passwordChangeRequired() ? "Замените временный пароль для продолжения работы" : null,
                (controller, form) -> {
                    controller.form = form;
                    controller.context = context;
                    controller.saved = saved;
                    form.onCancel(cancelled);
                    form.onSave(controller::save);
                });
    }

    private void save() {
        try {
            String old = currentPassword.getText(), next = newPassword.getText();
            PasswordHasher.validate(next);
            if (!next.equals(confirmation.getText())) {
                form.invalid(confirmation);
                throw new IllegalArgumentException("Подтверждение пароля не совпадает");
            }
            form.submit(context.getTasks(), () -> {
                context.user(context.requireAuth().changePassword(context.user(), old, next));
                return null;
            }, feedback, saved);
        } catch (IllegalArgumentException error) {
            feedback.setText(error.getMessage());
        }
    }
}
