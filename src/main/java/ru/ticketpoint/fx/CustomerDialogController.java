package ru.ticketpoint.fx;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Window;
import ru.ticketpoint.model.CreateCustomerCommand;

public final class CustomerDialogController {
    @FXML private TextField name;
    @FXML private Label errorLabel;
    private FormDialog form;
    private AppContext context;
    private Runnable saved;

    static void show(Window owner, AppContext context, Runnable saved) {
        FormDialog.<CustomerDialogController>show(owner, "customer-dialog.fxml", "Новый клиент", null, (controller, form) -> {
            controller.form = form;
            controller.context = context;
            controller.saved = saved;
            form.onSave(controller::save);
        });
    }

    private void save() {
        try {
            CreateCustomerCommand command = new CreateCustomerCommand(form.text(name, "Имя клиента", 120));
            form.submit(context.tasks(), () -> context.services().customers().create(command), errorLabel, saved);
        } catch (IllegalArgumentException error) {
            errorLabel.setText(error.getMessage());
        }
    }
}
