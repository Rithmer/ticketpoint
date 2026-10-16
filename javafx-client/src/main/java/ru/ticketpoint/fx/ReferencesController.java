package ru.ticketpoint.fx;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import ru.ticketpoint.model.Customer;
import ru.ticketpoint.model.Event;

import java.util.List;

public final class ReferencesController {
    @FXML private Button addCustomerButton;
    @FXML private Button addEventButton;
    @FXML private Button editCustomerButton;
    @FXML private Button deleteCustomerButton;
    @FXML private Button editEventButton;
    @FXML private Button deleteEventButton;
    @FXML private javafx.scene.layout.HBox customerHeader;
    @FXML private TableView<Customer> customerTable;
    @FXML private TableView<Event> eventTable;
    private AppContext context;
    private Runnable refresh;

    @FXML private void initialize() {
        customerTable.getColumns().add(TableColumns.column("Клиент", 190, Customer::name));
        customerTable.getColumns().add(TableColumns.column("Код клиента", 370, Customer::customerCode));
        customerTable.setPlaceholder(new Label("Клиентов пока нет"));
        eventTable.getColumns().add(TableColumns.column("Название", 240, Event::title));
        eventTable.getColumns().add(TableColumns.column("Место", 180, Event::venue));
        eventTable.getColumns().add(TableColumns.date("Дата", Event::startsAt));
        eventTable.getColumns().add(TableColumns.column("Цена, ₽", 110, Event::price));
        eventTable.setPlaceholder(new Label("Мероприятий пока нет"));
        customerTable.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            editCustomerButton.setDisable(!canManage() || b == null); deleteCustomerButton.setDisable(!canManage() || b == null);
        });
        eventTable.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            editEventButton.setDisable(!canManage() || b == null); deleteEventButton.setDisable(!canManage() || b == null);
        });
    }

    private boolean canManage() { return context != null && (context.auth() == null || context.isAdmin()); }

    void configure(AppContext context, Runnable refresh) {
        this.context = context;
        this.refresh = refresh;
        for (Button button : List.of(addCustomerButton, addEventButton, editCustomerButton, deleteCustomerButton, editEventButton, deleteEventButton)) {
            button.setVisible(canManage()); button.setManaged(canManage());
        }
        customerHeader.setVisible(!context.isCustomer()); customerHeader.setManaged(!context.isCustomer());
        customerTable.setVisible(!context.isCustomer()); customerTable.setManaged(!context.isCustomer());
    }

    void populate(List<Customer> customers, List<Event> events) {
        customerTable.setItems(FXCollections.observableArrayList(customers));
        eventTable.setItems(FXCollections.observableArrayList(context != null && context.isCustomer()
                ? events.stream().filter(e -> e.startsAt().isAfter(java.time.LocalDateTime.now(context.services().clock()))).toList() : events));
    }

    @FXML private void addCustomer() {
        CustomerDialogController.show(customerTable.getScene().getWindow(), context, refresh);
    }

    @FXML private void addEvent() {
        EventDialogController.show(eventTable.getScene().getWindow(), context, refresh);
    }

    @FXML private void refreshReferences() {
        refresh.run();
    }
    @FXML private void editCustomer() {
        Customer selected = customerTable.getSelectionModel().getSelectedItem();
        if (selected != null) CustomerDialogController.show(customerTable.getScene().getWindow(), context, refresh, selected);
    }
    @FXML private void editEvent() {
        Event selected = eventTable.getSelectionModel().getSelectedItem();
        if (selected != null) EventDialogController.show(eventTable.getScene().getWindow(), context, refresh, selected);
    }
    @FXML private void deleteCustomer() {
        Customer selected = customerTable.getSelectionModel().getSelectedItem();
        if (selected != null) remove(selected.name(), () -> context.services().customers().delete(selected.id()));
    }
    @FXML private void deleteEvent() {
        Event selected = eventTable.getSelectionModel().getSelectedItem();
        if (selected != null) remove(selected.title(), () -> context.services().events().delete(selected.id()));
    }
    private void remove(String name, Runnable action) {
        var owner = eventTable.getScene().getWindow();
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION, "Удалить «" + name + "»? Связанные с билетами записи удалить нельзя.", ButtonType.OK, ButtonType.CANCEL);
        confirmation.initOwner(owner); confirmation.setHeaderText("Удаление записи");
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK)
            context.tasks().execute(() -> { action.run(); return null; }, ignored -> refresh.run(), error -> UiErrors.show(owner, error));
    }
}
