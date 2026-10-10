package ru.ticketpoint.fx;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ru.ticketpoint.config.AppLogging;
import ru.ticketpoint.config.DatabaseConfig;

public final class TicketPointApplication extends Application {
    private AppContext context;
    private AppLogging logging;

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(UiResources.location("main.fxml"));
        Scene scene = new Scene(loader.load(), 1280, 780);
        scene.getStylesheets().add(UiResources.stylesheet());
        stage.setTitle("TicketPoint");
        stage.setMinWidth(960);
        stage.setMinHeight(680);
        stage.setScene(scene);
        stage.show();
        MainController controller = loader.getController();
        try {
            DatabaseConfig config = DatabaseConfig.fromEnvironment();
            logging = AppLogging.configure(config);
            context = new AppContext(ru.ticketpoint.config.ApplicationServices.jdbc(config), new FxTaskExecutor());
            controller.configure(context);
        } catch (IllegalArgumentException e) {
            controller.configurationError(e);
        }
    }

    @Override
    public void stop() {
        if (context != null) {
            context.tasks().close();
        }
        if (logging != null) {
            logging.close();
        }
    }
}
