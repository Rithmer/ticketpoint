package ru.ticketpoint.fx;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tab;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import ru.ticketpoint.model.*;
import java.io.IOException;
import java.util.List;

public final class MainController {
    @FXML private VBox workspace;
    @FXML private Label operationStatus;
    @FXML private HBox statusBar;
    @FXML private ProgressIndicator progress;
    @FXML private Tab statisticsTab;
    @FXML private Tab bookingsTab;
    @FXML private Tab referencesTab;
    private BookingsController bookingsController;
    private StatisticsController statisticsController;
    private ReferencesController referencesController;
    private AppContext context;
    private UiOperations operations;

    @FXML private void initialize() throws IOException {
        statusBar.visibleProperty().bind(operationStatus.textProperty().isNotEmpty());
        statusBar.managedProperty().bind(statusBar.visibleProperty());
        var bookingsView = UiResources.load("bookings.fxml", BookingsController.class);
        bookingsTab.setContent(bookingsView.root());
        bookingsController = bookingsView.controller();
        var referencesView = UiResources.load("references.fxml", ReferencesController.class);
        referencesTab.setContent(referencesView.root());
        referencesController = referencesView.controller();
        var statisticsView = UiResources.load("statistics.fxml", StatisticsController.class);
        statisticsTab.setContent(statisticsView.root());
        statisticsController = statisticsView.controller();
        workspace.setDisable(true);
    }

    void configure(AppContext context) {
        this.context = context;
        operations = new UiOperations(context.tasks(), workspace, operationStatus, progress);
        bookingsController.configure(context, this::refresh, operations);
        referencesController.configure(context, this::refresh);
        refresh();
    }

    void configurationError(Throwable error) {
        operationStatus.setText("Проверьте настройки в .env и перезапустите приложение через run.sh или run.ps1.");
        UiErrors.show(workspace.getScene().getWindow(), error);
    }

    private void refresh() {
        if (context == null) {
            return;
        }
        operations.run(() -> {
            context.services().preflight().verify();
            return new Snapshot(context.services().bookings().findAll(), context.services().customers().findAll(),
                    context.services().events().findAll(), context.services().bookings().statistics());
        }, "Загрузка данных из PostgreSQL…", snapshot -> {
            bookingsController.populate(snapshot.bookings(), snapshot.customers(), snapshot.events());
            referencesController.populate(snapshot.customers(), snapshot.events());
            statisticsController.populate(snapshot.statistics());
            operations.message("");
        });
    }

    private record Snapshot(List<Booking> bookings, List<Customer> customers,
                            List<Event> events, BookingStatistics statistics) { }
}
