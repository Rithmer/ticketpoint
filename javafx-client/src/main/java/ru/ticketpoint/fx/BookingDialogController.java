package ru.ticketpoint.fx;

import java.time.LocalDateTime;
import java.util.List;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Window;
import ru.ticketpoint.model.*;

public final class BookingDialogController {
    @FXML
    private ComboBox<Customer> customer;
    @FXML
    private ComboBox<Event> event;
    @FXML
    private ComboBox<BookingStatus> status;
    @FXML
    private Label price;
    @FXML
    private Label errorLabel;
    @FXML
    private Label editRules;
    private FormDialog form;
    private AppContext context;
    private Booking booking;
    private Runnable saved;

    @FXML
    private void initialize() {
        customer.setConverter(UiFormat.converter(UiFormat::customer, "Выберите клиента"));
        event.setConverter(UiFormat.converter(UiFormat::event, "Выберите мероприятие"));
        status.setConverter(UiFormat.converter(UiFormat::status, "Выберите статус"));
        event.valueProperty().addListener((o, a, b) -> updatePrice());
    }

    private void updatePrice() {
        Event selected = event.getValue();
        price.setText(selected == null ? "—" : (booking != null && selected.id().equals(booking.event().id())
                ? booking.priceAtBooking() : selected.price()) + " ₽");
    }

    static void show(Window owner, AppContext context, List<Customer> customers, List<Event> events,
                     Booking booking, Runnable saved) {
        show(owner, context, customers, events, booking, null, saved);
    }

    static void show(Window owner, AppContext context, List<Customer> customers, List<Event> events,
                     Booking booking, Event selectedEvent, Runnable saved) {
        FormDialog.<BookingDialogController>show(owner, "booking-dialog.fxml",
                booking == null ? "Новое бронирование" : "Редактирование бронирования",
                booking == null ? null : booking.ticketNumber(), (controller, form) -> {
                    controller.configure(form, context, customers, events, booking, saved);
                    if (selectedEvent != null) controller.event.setValue(selectedEvent);
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
        event.getItems().setAll(events.stream().filter(e -> e.active() && e.startsAt()
                .isAfter(LocalDateTime.now(context.services().clock()))).toList());
        if (booking == null) {
            editRules.setText("Заявка поступит менеджеру для подтверждения. Цена определяется тарифом мероприятия.");
            if (context.isCustomer()) {
                customer.setValue(customers.stream().filter(c -> c.id().equals(context.user().customerId())).findFirst().orElse(null));
                customer.setDisable(true);
            }
            status.getItems().setAll(BookingStatus.NEW);
            status.setValue(BookingStatus.NEW);
            status.setDisable(true);
        } else {
            customer.setValue(customers.stream().filter(c -> c.id().equals(booking.customer().id()))
                    .findFirst().orElse(booking.customer()));
            event.setValue(events.stream().filter(e -> e.id().equals(booking.event().id()))
                    .findFirst().orElse(booking.event()));
            boolean editable = booking.status() == BookingStatus.NEW && booking.event().startsAt()
                    .isAfter(LocalDateTime.now(context.services().clock()));
            customer.setDisable(!editable || context.isCustomer());
            event.setDisable(!editable);
            editRules.setText(context.isCustomer()
                    ? (editable ? "Можно выбрать другое мероприятие по его текущей цене или отменить заявку. Подтверждает заявку менеджер."
                    : "Заявка подтверждена менеджером. До оплаты и начала мероприятия её можно отменить. По другим вопросам обратитесь к менеджеру.")
                    : (editable ? "Можно исправить клиента и мероприятие, подтвердить или отменить заявку. При смене мероприятия применяется его текущая цена."
                    : "Реквизиты зафиксированы. Подтверждённую заявку можно вернуть на исправление, отметить оплату или отменить. Оплаченный билет можно только отменить."));
            status.getItems().setAll(context.statuses(booking));
            status.setValue(context.isCustomer() && booking.status() == BookingStatus.CONFIRMED
                    ? BookingStatus.CANCELLED : booking.status());
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
                    ? "Не удалось найти профиль покупателя. Обратитесь к администратору."
                    : "Выберите клиента из списка.");
            return;
        }
        if (selectedEvent == null) {
            form.invalid(event);
            errorLabel.setText(event.getItems().isEmpty()
                    ? "Сейчас нет мероприятий, доступных для бронирования. Загляните позже."
                    : "Выберите мероприятие из списка.");
            return;
        }
        if (selectedStatus == null) {
            form.invalid(status);
            errorLabel.setText("Выберите статус бронирования.");
            return;
        }
        if (booking != null && selectedStatus == booking.status()
                && selectedCustomer.id().equals(booking.customer().id())
                && selectedEvent.id().equals(booking.event().id())) {
            form.close();
            return;
        }
        form.submit(context.getTasks(), () -> booking == null
                        ? context.services().bookings().createBooking(selectedCustomer.id(), selectedEvent.id())
                        : context.services().bookings().updateBooking(booking, selectedCustomer.id(), selectedEvent.id(), selectedStatus),
                errorLabel, saved);
    }
}
