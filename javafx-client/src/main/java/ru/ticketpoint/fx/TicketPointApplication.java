package ru.ticketpoint.fx;

import java.io.IOException;
import java.util.List;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import ru.ticketpoint.config.AppLogging;

public final class TicketPointApplication extends Application {
    private AppContext context;
    private FxTaskExecutor loginTasks;
    private FxTaskExecutor sessionTasks;
    private AppLogging logging;
    private Stage stage;
    private boolean loggingOut;
    private final PauseTransition expiry = new PauseTransition(Duration.minutes(30));

    @Override
    public void start(Stage stage) throws Exception {
        this.stage = stage;
        stage.setTitle("Билетная касса");
        stage.setMinWidth(600);
        stage.setMinHeight(600);
        showLogin();
        stage.show();
    }

    private void showLogin() throws IOException {
        expiry.stop();
        for (var window : List.copyOf(Window.getWindows())) {
            if (window instanceof Stage child && child.getOwner() == stage) child.hide();
        }
        if (context != null) {
            context.logout();
            context = null;
        }
        if (loginTasks != null) loginTasks.close();
        loginTasks = new FxTaskExecutor();
        FXMLLoader loader = new FXMLLoader(UiResources.location("login.fxml"));
        Scene scene = new Scene(loader.load(), 720, 680);
        scene.getStylesheets().add(UiResources.stylesheet());
        stage.setMinWidth(600);
        stage.setMinHeight(600);
        stage.setScene(scene);
        LoginController controller = loader.getController();
        controller.configure(loginTasks, result -> {
            if (logging != null) logging.close();
            logging = AppLogging.configure(result.config());
            sessionTasks = new FxTaskExecutor();
            context = new AppContext(result.config(), sessionTasks, result.auth(), result.user());
            context.getTasks().onSessionInvalid(this::logout);
            expiry.setOnFinished(event -> logout());
            expiry.playFromStart();
            if (result.user() != null && result.user().passwordChangeRequired()) {
                PasswordDialogController.show(stage, context, this::openWorkspace, this::logout);
            } else openWorkspace();
        });
    }

    private void openWorkspace() {
        try {
            FXMLLoader loader = new FXMLLoader(UiResources.location("main.fxml"));
            Scene scene = new Scene(loader.load(), 1280, 780);
            scene.getStylesheets().add(UiResources.stylesheet());
            stage.setMinWidth(960);
            stage.setMinHeight(680);
            stage.setScene(scene);
            MainController controller = loader.getController();
            controller.configure(context);
            controller.onLogout(this::logout);
        } catch (IOException error) {
            UiErrors.show(stage, error);
            logout();
        }
    }

    void logout() {
        if (loggingOut) return;
        loggingOut = true;
        try {
            showLogin();
        } catch (IOException error) {
            UiErrors.show(stage, error);
            stage.close();
        } finally {
            loggingOut = false;
        }
    }

    @Override
    public void stop() {
        if (context != null) context.logout();
        if (sessionTasks != null) sessionTasks.close();
        if (loginTasks != null) loginTasks.close();
        if (logging != null) logging.close();
    }
}
