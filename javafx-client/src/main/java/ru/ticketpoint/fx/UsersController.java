package ru.ticketpoint.fx;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import ru.ticketpoint.auth.*;

public final class UsersController {
    @FXML
    private VBox workspace;
    @FXML
    private TableView<User> table;
    @FXML
    private Label feedback;
    @FXML
    private ProgressIndicator progress;
    @FXML
    private Button accessButton;
    @FXML
    private Button editButton;
    private AppContext context;
    private UiOperations operations;

    @FXML
    private void initialize() {
        table.getColumns().add(TableColumns.column("Логин", 180, User::login));
        table.getColumns().add(TableColumns.column("Имя", 240, User::name));
        table.getColumns().add(TableColumns.column("Роль", 160, u -> UiFormat.role(u.role())));
        table.getColumns().add(TableColumns.column("Доступ", 130, u -> u.enabled() ? "Активен" : "Отключён"));
        table.getColumns().add(TableColumns.column("Пароль", 180, u -> u.passwordChangeRequired() ? "Временный" : "Постоянный"));
        table.setPlaceholder(new Label("Нет пользователей"));
        table.getSelectionModel().selectedItemProperty().addListener((o, a, u) -> {
            accessButton.setDisable(u == null || u.role() == Role.ADMIN);
            editButton.setDisable(u == null);
            accessButton.setText(u != null && !u.enabled() ? "Включить доступ" : "Отключить доступ");
        });
    }

    void configure(AppContext context) {
        this.context = context;
        operations = new UiOperations(context.getTasks(), workspace, feedback, progress);
        refresh();
    }

    @FXML
    private void refresh() {
        operations.run(() -> context.requireAuth().list(context.user()), "Загрузка пользователей…", users -> {
            table.setItems(FXCollections.observableArrayList(users));
            operations.message("");
        });
    }

    @FXML
    private void addUser() {
        UserDialogController.show(table.getScene().getWindow(), context, this::refresh);
    }

    @FXML
    private void editUser() {
        User selected = table.getSelectionModel().getSelectedItem();
        if (selected != null) UserDialogController.show(table.getScene().getWindow(), context, this::refresh, selected);
    }

    @FXML
    private void toggleAccess() {
        User user = table.getSelectionModel().getSelectedItem();
        if (user == null) return;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, (user.enabled() ? "Отключить" : "Включить")
                + " доступ для «" + user.login() + "»?", ButtonType.OK, ButtonType.CANCEL);
        alert.initOwner(table.getScene().getWindow());
        alert.setHeaderText("Изменение доступа");
        if (alert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        operations.run(() -> {
                    context.requireAuth().setEnabled(context.user(), user.id(), !user.enabled());
                    return null;
                },
                "Изменение доступа…", value -> refresh());
    }
}
