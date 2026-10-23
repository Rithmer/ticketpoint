package ru.ticketpoint.fx;

import java.util.Locale;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import ru.ticketpoint.model.BookingStatistics;

public final class StatisticsController {
    @FXML
    private Label total;
    @FXML
    private Label fresh;
    @FXML
    private Label confirmed;
    @FXML
    private Label scope;
    @FXML
    private Label paid;
    @FXML
    private Label cancelled;
    @FXML
    private Label expensive;
    @FXML
    private Label paidShare;
    private Runnable refresh;

    void configure(Runnable refresh) {
        this.refresh = refresh;
    }

    void personal(boolean personal) {
        scope.setText(personal ? "Показатели только по вашим заявкам и билетам." : "Показатели по всей базе данных, независимо от фильтров списка.");
    }

    @FXML
    private void refreshStatistics() {
        refresh.run();
    }

    void populate(BookingStatistics statistics) {
        total.setText(Long.toString(statistics.total()));
        fresh.setText(Long.toString(statistics.newBookings()));
        confirmed.setText(Long.toString(statistics.confirmed()));
        paid.setText(Long.toString(statistics.paid()));
        cancelled.setText(Long.toString(statistics.cancelled()));
        expensive.setText(Long.toString(statistics.expensiveTickets()));
        paidShare.setText(statistics.total() == 0 ? "0 %"
                : String.format(Locale.forLanguageTag("ru"), "%.1f %%", 100.0 * statistics.paid() / statistics.total()));
    }
}
