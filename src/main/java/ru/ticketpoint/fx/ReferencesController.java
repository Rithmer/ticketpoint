package ru.ticketpoint.fx;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import ru.ticketpoint.model.Customer;
import ru.ticketpoint.model.Event;

import java.util.List;

public final class ReferencesController {
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
    }

    void configure(AppContext context, Runnable refresh) {
        this.context = context;
        this.refresh = refresh;
    }

    void populate(List<Customer> customers, List<Event> events) {
        customerTable.setItems(FXCollections.observableArrayList(customers));
        eventTable.setItems(FXCollections.observableArrayList(events));
    }

    @FXML private void addCustomer() {
        CustomerDialogController.show(customerTable.getScene().getWindow(), context, refresh);
    }

    @FXML private void addEvent() {
        EventDialogController.show(eventTable.getScene().getWindow(), context, refresh);
    }
}
