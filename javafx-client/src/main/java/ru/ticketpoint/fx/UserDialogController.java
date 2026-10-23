package ru.ticketpoint.fx;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import ru.ticketpoint.auth.*;

public final class UserDialogController {
    @FXML
    private TextField login;
    @FXML
    private TextField name;
    @FXML
    private PasswordField password;
    @FXML
    private PasswordField confirmation;
    @FXML
    private ComboBox<Role> role;
    @FXML
    private Label feedback;
    @FXML
    private VBox credentials;
    private User original;
    private AppContext context;
    private FormDialog form;
    private Runnable saved;

    @FXML
    private void initialize() {
        role.setConverter(UiFormat.converter(UiFormat::role, "Выберите роль"));
        role.getItems().setAll(Role.values());
        role.setValue(Role.USER);
    }

    static void show(Window owner, AppContext context, Runnable saved) {
        show(owner, context, saved, null);
    }

    static void show(Window owner, AppContext context, Runnable saved, User original) {
        FormDialog.<UserDialogController>show(owner, "user-dialog.fxml", original == null ? "Новый пользователь" : "Изменить пользователя", null, (controller, form) -> {
            controller.original = original;
            if (original != null) {
                controller.login.setText(original.login());
                controller.login.setDisable(true);
                controller.name.setText(original.name());
                controller.role.setValue(original.role());
                controller.role.setDisable(original.role() == Role.ADMIN);
                controller.credentials.setVisible(false);
                controller.credentials.setManaged(false);
            }
            controller.context = context;
            controller.form = form;
            controller.saved = saved;
            form.onSave(controller::save);
        });
    }

    private void save() {
        try {
            String username = AuthService.login(login.getText());
            String displayName = form.text(name, "Имя пользователя", 120);
            Role selectedRole = role.getValue();
            if (original != null) {
                form.submit(context.getTasks(), () -> {
                    User updated = context.requireAuth().update(context.user(), original, displayName, selectedRole);
                    if (updated.id() == context.user().id()) context.user(updated);
                    return updated;
                }, feedback, saved);
                return;
            }
            String secret = password.getText();
            PasswordHasher.validate(secret);
            if (!secret.equals(confirmation.getText())) {
                form.invalid(confirmation);
                throw new IllegalArgumentException("Подтверждение пароля не совпадает");
            }
            form.submit(context.getTasks(), () -> context.requireAuth().create(context.user(), username, displayName, secret, selectedRole), feedback, saved);
        } catch (IllegalArgumentException error) {
            feedback.setText(error.getMessage());
        }
    }
}
