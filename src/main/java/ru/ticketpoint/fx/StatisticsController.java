package ru.ticketpoint.fx;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import ru.ticketpoint.model.BookingStatistics;

public final class StatisticsController {
    @FXML private Label total;
    @FXML private Label fresh;
    @FXML private Label paid;
    @FXML private Label cancelled;
    @FXML private Label expensive;
    @FXML private Label paidShare;

    void populate(BookingStatistics statistics) {
        total.setText(Long.toString(statistics.total()));
        fresh.setText(Long.toString(statistics.newBookings()));
        paid.setText(Long.toString(statistics.paid()));
        cancelled.setText(Long.toString(statistics.cancelled()));
        expensive.setText(Long.toString(statistics.expensiveTickets()));
        paidShare.setText(statistics.total() == 0 ? "0 %"
                : String.format(java.util.Locale.forLanguageTag("ru"), "%.1f %%", 100.0 * statistics.paid() / statistics.total()));
    }
}
