package ru.ticketpoint.fx;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Window;
import ru.ticketpoint.model.CreateCustomerCommand;
import ru.ticketpoint.model.Customer;

public final class CustomerDialogController {
    @FXML
    private TextField name;
    @FXML
    private Label errorLabel;
    private FormDialog form;
    private AppContext context;
    private Runnable saved;
    private Customer original;

    static void show(Window owner, AppContext context, Runnable saved) {
        show(owner, context, saved, null);
    }

    static void show(Window owner, AppContext context, Runnable saved, Customer original) {
        FormDialog.<CustomerDialogController>show(owner, "customer-dialog.fxml", original == null ? "Новый клиент" : "Изменить клиента", null, (controller, form) -> {
            controller.original = original;
            if (original != null) controller.name.setText(original.name());
            controller.form = form;
            controller.context = context;
            controller.saved = saved;
            form.onSave(controller::save);
        });
    }

    private void save() {
        try {
            CreateCustomerCommand command = new CreateCustomerCommand(form.text(name, "Имя клиента", 120));
            form.submit(context.getTasks(), () -> original == null ? context.services().customers().create(command) : context.services().customers().update(original.id(), command), errorLabel, saved);
        } catch (IllegalArgumentException error) {
            errorLabel.setText(error.getMessage());
        }
    }
}
