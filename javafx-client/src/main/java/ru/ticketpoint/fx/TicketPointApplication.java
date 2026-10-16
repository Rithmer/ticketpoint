package ru.ticketpoint.fx;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ru.ticketpoint.config.AppLogging;

public final class TicketPointApplication extends Application {
    private AppContext context;
    private FxTaskExecutor loginTasks;
    private AppLogging logging;
    private Stage stage;
    private final javafx.animation.PauseTransition expiry = new javafx.animation.PauseTransition(javafx.util.Duration.minutes(30));
    @Override public void start(Stage stage) throws Exception {
        this.stage = stage; stage.setTitle("TicketPoint"); stage.setMinWidth(600); stage.setMinHeight(600);
        showLogin(); stage.show();
    }
    private void showLogin() throws java.io.IOException {
        expiry.stop();
        if (context != null) { context.logout(); context = null; }
        if (loginTasks != null) loginTasks.close();
        loginTasks = new FxTaskExecutor();
        FXMLLoader loader = new FXMLLoader(UiResources.location("login.fxml"));
        Scene scene = new Scene(loader.load(), 720, 680); scene.getStylesheets().add(UiResources.stylesheet());
        stage.setMinWidth(600); stage.setMinHeight(600); stage.setScene(scene);
        LoginController controller = loader.getController();
        controller.configure(loginTasks, result -> {
            if (logging != null) logging.close();
            logging = AppLogging.configure(result.config());
            context = new AppContext(result.config(), new FxTaskExecutor(), result.auth(), result.user());
            context.tasks().onSessionInvalid(() -> {
                for (javafx.stage.Window window : java.util.List.copyOf(javafx.stage.Window.getWindows())) {
                    if (window != stage) window.hide();
                }
                logout();
            });
            expiry.setOnFinished(event -> logout()); expiry.playFromStart();
            if (result.user().passwordChangeRequired()) {
                PasswordDialogController.show(stage, context, this::openWorkspace, this::logout);
            } else openWorkspace();
        });
    }
    private void openWorkspace() {
        try {
            FXMLLoader loader = new FXMLLoader(UiResources.location("main.fxml"));
            Scene scene = new Scene(loader.load(), 1280, 780); scene.getStylesheets().add(UiResources.stylesheet());
            stage.setMinWidth(960); stage.setMinHeight(680); stage.setScene(scene);
            MainController controller = loader.getController(); controller.configure(context); controller.onLogout(this::logout);
        } catch (java.io.IOException error) { UiErrors.show(stage, error); logout(); }
    }
    private void logout() {
        try { showLogin(); } catch (java.io.IOException error) { UiErrors.show(stage, error); stage.close(); }
    }
    @Override public void stop() {
        if (context != null) context.logout();
        if (loginTasks != null) loginTasks.close();
        if (logging != null) logging.close();
    }
}
