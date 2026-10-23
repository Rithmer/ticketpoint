package ru.ticketpoint.fx;

import java.io.IOException;
import java.util.List;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import ru.ticketpoint.model.*;

public final class MainController {
    @FXML
    private VBox workspace;
    @FXML
    private TabPane tabs;
    @FXML
    private Label userInfo;
    @FXML
    private javafx.scene.control.Button passwordButton;
    @FXML
    private javafx.scene.control.Button sessionButton;
    private Runnable logout;
    @FXML
    private Label operationStatus;
    @FXML
    private HBox statusBar;
    @FXML
    private ProgressIndicator progress;
    @FXML
    private Tab statisticsTab;
    @FXML
    private Tab bookingsTab;
    @FXML
    private Tab referencesTab;
    private BookingsController bookingsController;
    private StatisticsController statisticsController;
    private ReferencesController referencesController;
    private AppContext context;
    private UiOperations operations;

    @FXML
    private void initialize() throws IOException {
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
        boolean guest = context.user() == null && context.auth() != null;
        userInfo.setText(guest ? "Гость • Наблюдатель" : "");
        passwordButton.setVisible(!guest);
        passwordButton.setManaged(!guest);
        sessionButton.setText(guest ? "Войти" : "Выйти");
        if (context.user() != null) userInfo.setText(context.user().name() + " • " + switch (context.user().role()) {
            case USER -> "Покупатель";
            case VIEWER -> "Наблюдатель";
            case MANAGER -> "Менеджер";
            case ADMIN -> "Администратор";
        });
        if (context.isAdmin() && tabs.getTabs().stream().noneMatch(t -> "Пользователи".equals(t.getText()))) {
            try {
                var users = UiResources.load("users.fxml", UsersController.class);
                tabs.getTabs().add(new Tab("Пользователи", users.root()));
                users.controller().configure(context);
            } catch (IOException error) {
                UiErrors.show(workspace.getScene().getWindow(), error);
            }
        }
        operations = new UiOperations(context.getTasks(), workspace, operationStatus, progress);
        bookingsController.configure(context, this::refresh, operations);
        referencesController.configure(context, this::refresh);
        statisticsController.configure(this::refresh);
        statisticsController.personal(context.isCustomer());
        bookingsTab.setText(context.isCustomer() ? "Мои билеты" : "Заявки и билеты");
        referencesTab.setText(context.isCustomer() ? "Афиша" : "Клиенты и мероприятия");
        refresh();
    }


    private void refresh() {
        if (context == null) {
            return;
        }
        operations.run(() -> {
            context.services().preflight().verify();
            return new Snapshot(context.services().bookings().findAll(), context.services().customers().findAll(),
                    context.services().events().findAll(), context.services().bookings().statistics());
        }, "Загрузка…", snapshot -> {
            bookingsController.populate(snapshot.bookings(), snapshot.customers(), snapshot.events());
            referencesController.populate(snapshot.customers(), snapshot.events());
            statisticsController.populate(snapshot.statistics());
            operations.message("");
        });
    }

    void onLogout(Runnable action) {
        logout = action;
    }

    @FXML
    private void signOut() {
        if (logout != null) logout.run();
    }

    @FXML
    private void changePassword() {
        if (context != null && context.auth() != null) {
            PasswordDialogController.show(workspace.getScene().getWindow(), context, () -> {
            }, () -> {
            });
        }
    }

    private record Snapshot(List<Booking> bookings, List<Customer> customers,
                            List<Event> events, BookingStatistics statistics) {
    }
}
