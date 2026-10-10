package ru.ticketpoint.fx;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Window;
import ru.ticketpoint.model.*;

import java.util.List;

public final class BookingDialogController {
    @FXML private ComboBox<Customer> customer;
    @FXML private ComboBox<Event> event;
    @FXML private ComboBox<BookingStatus> status;
    @FXML private Label price;
    @FXML private Label errorLabel;
    private FormDialog form;
    private AppContext context;
    private Booking booking;
    private Runnable saved;

    @FXML private void initialize() {
        customer.setConverter(UiFormat.converter(UiFormat::customer, "Выберите клиента"));
        event.setConverter(UiFormat.converter(UiFormat::event, "Выберите мероприятие"));
        status.setConverter(UiFormat.converter(UiFormat::status, "Выберите статус"));
        event.valueProperty().addListener((o, a, b) -> {
            if (booking == null) price.setText(b == null ? "—" : b.price() + " ₽");
        });
    }

    static void show(Window owner, AppContext context, List<Customer> customers, List<Event> events,
                     Booking booking, Runnable saved) {
        FormDialog.<BookingDialogController>show(owner, "booking-dialog.fxml",
                booking == null ? "Новое бронирование" : "Редактирование бронирования",
                booking == null ? null : booking.ticketNumber(), (controller, form) -> {
                    controller.configure(form, context, customers, events, booking, saved);
                    form.onSave(controller::save);
                });
    }

    private void configure(FormDialog form, AppContext context, List<Customer> customers,
                           List<Event> events, Booking booking, Runnable saved) {
        this.form = form;
        this.context = context;
        this.booking = booking;
        this.saved = saved;
        customer.getItems().setAll(customers);
        event.getItems().setAll(events);
        if (booking == null) {
            status.getItems().setAll(BookingStatus.NEW);
            status.setValue(BookingStatus.NEW);
            status.setDisable(true);
        } else {
            customer.setValue(customers.stream().filter(c -> c.id().equals(booking.customer().id()))
                    .findFirst().orElse(booking.customer()));
            event.setValue(events.stream().filter(e -> e.id().equals(booking.event().id()))
                    .findFirst().orElse(booking.event()));
            customer.setDisable(true);
            event.setDisable(true);
            status.getItems().setAll(booking.status().allowedTransitions());
            status.setValue(booking.status());
            price.setText(booking.priceAtBooking() + " ₽");
        }
    }

    private void save() {
        Customer selectedCustomer = customer.getValue();
        Event selectedEvent = event.getValue();
        BookingStatus selectedStatus = status.getValue();
        if (selectedCustomer == null) {
            form.invalid(customer);
            errorLabel.setText(customer.getItems().isEmpty()
                    ? "Клиентов пока нет. Сначала добавьте клиента на вкладке «Клиенты и мероприятия»."
                    : "Выберите клиента из списка.");
            return;
        }
        if (selectedEvent == null) {
            form.invalid(event);
            errorLabel.setText(event.getItems().isEmpty()
                    ? "Мероприятий пока нет. Сначала добавьте мероприятие на вкладке «Клиенты и мероприятия»."
                    : "Выберите мероприятие из списка.");
            return;
        }
        if (selectedStatus == null) {
            form.invalid(status);
            errorLabel.setText("Выберите статус бронирования.");
            return;
        }
        if (booking != null && selectedStatus == booking.status()) {
            form.close();
            return;
        }
        form.submit(context.tasks(), () -> booking == null
                ? context.services().bookings().createBooking(selectedCustomer.id(), selectedEvent.id())
                : context.services().bookings().changeStatus(booking.id(), selectedStatus), errorLabel, saved);
    }
}
