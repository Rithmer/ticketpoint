package ru.ticketpoint.fx;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Window;
import ru.ticketpoint.model.CreateEventCommand;
import ru.ticketpoint.model.Event;
import ru.ticketpoint.model.Validation;

public final class EventDialogController {
    @FXML
    private TextField name;
    @FXML
    private TextField venue;
    @FXML
    private DatePicker day;
    @FXML
    private TextField time;
    @FXML
    private TextField price;
    @FXML
    private Label errorLabel;
    @FXML
    private Label zoneInfo;
    private FormDialog form;
    private AppContext context;
    private Runnable saved;
    private Event original;

    static void show(Window owner, AppContext context, Runnable saved) {
        show(owner, context, saved, null);
    }

    static void show(Window owner, AppContext context, Runnable saved, Event original) {
        FormDialog.<EventDialogController>show(owner, "event-dialog.fxml", original == null ? "Новое мероприятие" : "Изменить мероприятие", null, (controller, form) -> {
            controller.original = original;
            if (original != null) {
                controller.name.setText(original.title());
                controller.venue.setText(original.venue());
                controller.day.setValue(original.startsAt().toLocalDate());
                controller.time.setText(original.startsAt().toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")));
                controller.price.setText(original.price().toPlainString());
            }
            controller.form = form;
            controller.context = context;
            controller.saved = saved;
            controller.zoneInfo.setText("Часовой пояс: " + context.services().clock().getZone().getId());
            form.onSave(controller::save);
        });
    }

    private void save() {
        try {
            if (day.getValue() == null) {
                form.invalid(day);
                throw new IllegalArgumentException("Выберите дату мероприятия");
            }
            CreateEventCommand command = new CreateEventCommand(form.text(name, "Название мероприятия", 200),
                    form.text(venue, "Место проведения", 200),
                    LocalDateTime.of(day.getValue(), parseTime()), parsePrice());
            form.submit(context.getTasks(), () -> original == null ? context.services().events().create(command) : context.services().events().update(original.id(), command), errorLabel, saved);
        } catch (IllegalArgumentException error) {
            errorLabel.setText(error.getMessage());
        }
    }

    private LocalTime parseTime() {
        try {
            if (!time.getText().strip().matches("[0-9]{2}:[0-9]{2}")) {
                throw new DateTimeParseException("Неверный формат времени", time.getText(), 0);
            }
            return LocalTime.parse(time.getText().strip());
        } catch (DateTimeParseException error) {
            form.invalid(time);
            throw new IllegalArgumentException("Время: укажите часы и минуты в формате HH:mm, например 19:30", error);
        }
    }

    private BigDecimal parsePrice() {
        try {
            return Validation.price(new BigDecimal(price.getText().strip().replace(',', '.')));
        } catch (NumberFormatException error) {
            form.invalid(price);
            throw new IllegalArgumentException("Цена: введите число, например 1500,00", error);
        } catch (IllegalArgumentException error) {
            form.invalid(price);
            throw error;
        }
    }
}
