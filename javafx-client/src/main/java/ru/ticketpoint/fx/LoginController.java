package ru.ticketpoint.fx;

import java.time.Clock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import ru.ticketpoint.auth.*;
import ru.ticketpoint.config.*;
import ru.ticketpoint.model.Validation;
import ru.ticketpoint.repository.JdbcUserRepository;
import ru.ticketpoint.service.DatabasePreflight;

public final class LoginController {
    @FXML
    private VBox form;
    @FXML
    private TextField login;
    @FXML
    private PasswordField password;
    @FXML
    private Label feedback;
    @FXML
    private ProgressIndicator progress;
    @FXML
    private VBox registrationForm;
    @FXML
    private TextField registrationName;
    @FXML
    private TextField registrationLogin;
    @FXML
    private PasswordField registrationPassword;
    @FXML
    private PasswordField registrationConfirmation;
    @FXML
    private Button signInButton;
    @FXML
    private Button registerButton;
    private FxTaskExecutor tasks;
    private Consumer<Authenticated> signedIn;
    private Supplier<DatabaseConfig> database;
    private Supplier<AuthConfig> bootstrap;

    @FXML
    private void initialize() {
        signInButton.defaultButtonProperty().bind(form.visibleProperty());
        registerButton.defaultButtonProperty().bind(registrationForm.visibleProperty());
        progress.managedProperty().bind(progress.visibleProperty());
        feedback.visibleProperty().bind(feedback.textProperty().isNotEmpty());
        feedback.managedProperty().bind(feedback.visibleProperty());
        login.textProperty().addListener((o, a, b) -> {
            if (!progress.isVisible()) feedback.setText("");
        });
        password.textProperty().addListener((o, a, b) -> {
            if (!progress.isVisible()) feedback.setText("");
        });
    }

    void configure(FxTaskExecutor tasks, Consumer<Authenticated> signedIn) {
        configure(tasks, signedIn, DatabaseConfig::fromEnvironment, AuthConfig::fromEnvironment);
    }

    void configure(FxTaskExecutor tasks, Consumer<Authenticated> signedIn,
                   Supplier<DatabaseConfig> database,
                   Supplier<AuthConfig> bootstrap) {
        this.tasks = tasks;
        this.signedIn = signedIn;
        this.database = database;
        this.bootstrap = bootstrap;
    }

    @FXML
    private void signIn() {
        String enteredLogin = login.getText();
        String enteredPassword = password.getText();
        if (enteredLogin.isBlank() || enteredPassword.isEmpty()) {
            feedback.setText("Введите логин и пароль");
            return;
        }
        submit(auth -> auth.authenticate(enteredLogin, enteredPassword));
    }

    @FXML
    private void showRegistration() {
        switchForm(true);
    }

    @FXML
    private void showSignIn() {
        switchForm(false);
    }

    private void switchForm(boolean registering) {
        clearPasswords();
        feedback.setText("");
        form.setVisible(!registering);
        form.setManaged(!registering);
        registrationForm.setVisible(registering);
        registrationForm.setManaged(registering);
        if (registering) registrationName.requestFocus();
        else login.requestFocus();
    }

    @FXML
    private void register() {
        String name = registrationName.getText(), username = registrationLogin.getText();
        String secret = registrationPassword.getText(), confirmation = registrationConfirmation.getText();
        try {
            AuthService.login(username);
            Validation.text(name, "Имя", 120);
            PasswordHasher.validate(secret);
            if (!secret.equals(confirmation)) throw new IllegalArgumentException("Подтверждение пароля не совпадает");
        } catch (IllegalArgumentException error) {
            feedback.setText(error.getMessage());
            return;
        }
        submit(auth -> auth.register(username, name, secret, confirmation));
    }

    private void clearPasswords() {
        password.clear();
        registrationPassword.clear();
        registrationConfirmation.clear();
    }

    private void submit(Function<AuthService, User> action) {
        form.setDisable(true);
        registrationForm.setDisable(true);
        progress.setVisible(true);
        feedback.setText("Подключение…");
        tasks.execute(() -> {
            DatabaseConfig config = database.get();
            var connections = new JdbcConnectionProvider(config);
            new DatabasePreflight(connections).verify();
            AuthService auth = new AuthService(new JdbcUserRepository(connections), Clock.system(config.zone()));
            auth.initialize(bootstrap.get());
            return new Authenticated(config, auth, action.apply(auth));
        }, result -> {
            clearPasswords();
            form.setDisable(false);
            registrationForm.setDisable(false);
            progress.setVisible(false);
            feedback.setText("");
            signedIn.accept(result);
        }, error -> {
            clearPasswords();
            form.setDisable(false);
            registrationForm.setDisable(false);
            progress.setVisible(false);
            feedback.setText(UiErrors.message(error));
            if (registrationForm.isVisible()) registrationPassword.requestFocus();
            else password.requestFocus();
        });
    }

    record Authenticated(DatabaseConfig config, AuthService auth, User user) {
    }
}
