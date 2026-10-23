package ru.ticketpoint.fx;

import java.io.IOException;

import javafx.fxml.FXML;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.stage.Window;
import ru.ticketpoint.model.Booking;

public final class BookingDetailsController {
    @FXML
    private Label ticketNumber;
    @FXML
    private Label status;
    @FXML
    private Label createdAt;
    @FXML
    private Label bookingPrice;
    @FXML
    private Label customerName;
    @FXML
    private Label customerCode;
    @FXML
    private Label eventTitle;
    @FXML
    private Label venue;
    @FXML
    private Label startsAt;
    @FXML
    private Label eventPrice;
    @FXML
    private Label zoneInfo;

    static void show(Window owner, Booking booking, String zone) {
        try {
            var view = UiResources.load("booking-details.fxml", BookingDetailsController.class);
            view.controller().populate(booking, zone);
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.initOwner(owner);
            dialog.setTitle("Сведения о бронировании");
            dialog.getDialogPane().setContent(view.root());
            dialog.getDialogPane().getStylesheets().add(UiResources.stylesheet());
            dialog.getDialogPane().getButtonTypes().add(new ButtonType("Закрыть", ButtonBar.ButtonData.CANCEL_CLOSE));
            dialog.setResizable(true);
            dialog.showAndWait();
        } catch (IOException error) {
            UiErrors.show(owner, error);
        }
    }

    void populate(Booking booking, String zone) {
        ticketNumber.setText(booking.ticketNumber());
        status.setText(UiFormat.status(booking.status()));
        createdAt.setText(UiFormat.date(booking.createdAt()));
        bookingPrice.setText(booking.priceAtBooking().toPlainString() + " ₽");
        customerName.setText(booking.customer().name());
        customerCode.setText(booking.customer().customerCode());
        eventTitle.setText(booking.event().title());
        venue.setText(booking.event().venue());
        startsAt.setText(UiFormat.date(booking.event().startsAt()));
        eventPrice.setText(booking.event().price().toPlainString() + " ₽");
        zoneInfo.setText("Часовой пояс: " + zone);
    }
}
