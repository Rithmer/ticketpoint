package ru.ticketpoint.fx;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import ru.ticketpoint.config.DatabaseConfig;
import ru.ticketpoint.model.Booking;
import ru.ticketpoint.model.BookingStatus;
import ru.ticketpoint.model.Customer;
import ru.ticketpoint.model.Event;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "RUN_FX_TESTS", matches = "true")
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class FxIntegrationTest {
    private static String schema;
    private static AppContext context;
    private Stage stage;
    private FXMLLoader loader;
    private TableView<Booking> table;

    private static <T> T fx(Callable<T> work) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try { result.complete(work.call()); }
            catch (Throwable error) { result.completeExceptionally(error); }
        });
        return result.get(20, TimeUnit.SECONDS);
    }

    @BeforeAll static void setup() throws Exception {
        CompletableFuture<Void> started = new CompletableFuture<>();
        Platform.startup(() -> { Platform.setImplicitExit(false); started.complete(null); });
        started.get(20, TimeUnit.SECONDS);
        schema = "fx_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(System.getenv("TEST_DB_URL"),
                System.getenv("TEST_DB_USER"), System.getenv("TEST_DB_PASSWORD"))) {
            connection.createStatement().execute("CREATE SCHEMA " + schema);
            connection.setSchema(schema);
            try (var statement = connection.createStatement()) {
                statement.execute(Files.readString(Path.of("db/schema.sql")));
                statement.execute(Files.readString(Path.of("db/seed.sql")));
            }
        }
        String url = System.getenv("TEST_DB_URL");
        context = new AppContext(ru.ticketpoint.config.ApplicationServices.jdbc(new DatabaseConfig(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema,
                System.getenv("TEST_DB_USER"), System.getenv("TEST_DB_PASSWORD"), ZoneId.of("Europe/Moscow"), 5, 15, 30)), new FxTaskExecutor());
    }

    @BeforeEach void openWindow() throws Exception {
        fx(() -> {
            loader = new FXMLLoader(UiResources.location("main.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root, 1280, 780);
            scene.getStylesheets().add(UiResources.stylesheet());
            stage = new Stage();
            stage.setScene(scene);
            stage.show();
            table = table();
            MainController controller = loader.getController();
            controller.configure(context);
            return null;
        });
        awaitLoaded();
    }

    private Object element(String id) {
        Object value = loader.getNamespace().get(id);
        return value != null ? value : stage.getScene().lookup("#" + id);
    }

    @SuppressWarnings("unchecked")
    private TableView<Booking> table() { return (TableView<Booking>) element("bookingTable"); }

    private void awaitLoaded() throws Exception {
        CompletableFuture<Void> loaded = new CompletableFuture<>();
        fx(() -> {
            ProgressIndicator progress = (ProgressIndicator) element("progress");
            if (!progress.isVisible()) {
                loaded.complete(null);
            } else {
                javafx.beans.value.ChangeListener<Boolean> listener = new javafx.beans.value.ChangeListener<>() {
                    @Override public void changed(javafx.beans.value.ObservableValue<? extends Boolean> value,
                                                  Boolean previous, Boolean visible) {
                        if (!visible) {
                            progress.visibleProperty().removeListener(this);
                            loaded.complete(null);
                        }
                    }
                };
                progress.visibleProperty().addListener(listener);
            }
            return null;
        });
        loaded.get(15, TimeUnit.SECONDS);
        assertFalse(fx(() -> ((Label) element("operationStatus")).getText().startsWith("Ошибка")));
    }

    @AfterEach void closeWindow() throws Exception {
        fx(() -> {
            for (Window window : java.util.List.copyOf(Window.getWindows())) window.hide();
            return null;
        });
    }

    @AfterAll static void cleanup() throws Exception {
        if (context != null) context.tasks().close();
        if (schema != null) {
            try (var connection = DriverManager.getConnection(System.getenv("TEST_DB_URL"),
                    System.getenv("TEST_DB_USER"), System.getenv("TEST_DB_PASSWORD"));
                 var statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
        Platform.exit();
    }

    @Test void allFxmlFormsLoadAndMainFiltersSortAndStatisticsWork() throws Exception {
        fx(() -> {
            assertEquals(10, table.getItems().size());
            TextField search = (TextField) element("searchField");
            search.setText("ДЖАЗОВЫЙ");
            assertEquals(4, table.getItems().size());
            search.clear();
            @SuppressWarnings("unchecked") ComboBox<BookingStatus> status = (ComboBox<BookingStatus>) element("statusFilter");
            status.setValue(BookingStatus.PAID);
            assertEquals(3, table.getItems().size());
            status.setValue(null);
            DatePicker from = (DatePicker) element("dateFrom");
            DatePicker to = (DatePicker) element("dateTo");
            from.setValue(LocalDate.now().plusDays(10));
            to.setValue(LocalDate.now());
            assertEquals(0, table.getItems().size());
            assertFalse(((Label) element("filterError")).getText().isEmpty());
            from.setValue(null);
            to.setValue(null);
            TableColumn<Booking, ?> date = table.getColumns().get(4);
            table.getSortOrder().add(date);
            table.sort();
            for (int i = 1; i < table.getItems().size(); i++) {
                assertFalse(table.getItems().get(i).event().startsAt()
                        .isBefore(table.getItems().get(i - 1).event().startsAt()));
            }
            table.getSelectionModel().select(table.getItems().stream()
                    .filter(b -> b.status() == BookingStatus.CANCELLED).findFirst().orElseThrow());
            assertTrue(((Button) element("editButton")).isDisabled());
            for (String resource : new String[]{"booking-dialog.fxml", "customer-dialog.fxml", "event-dialog.fxml"}) {
                assertNotNull(new FXMLLoader(UiResources.location(resource)).load());
            }
            TabPane tabs = (TabPane) element("tabs");
            tabs.getSelectionModel().select((Tab) element("statisticsTab"));
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            assertEquals("10", ((Label) stage.getScene().lookup("#total")).getText());
            assertEquals("30,0 %", ((Label) stage.getScene().lookup("#paidShare")).getText());
            snapshot("statistics.png");
            tabs.getSelectionModel().select(0);
            table.getSelectionModel().clearSelection();
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            snapshot("bookings.png");
            stage.setWidth(960);
            stage.setHeight(680);
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            return null;
        });
        CompletableFuture<Void> pulse = new CompletableFuture<>();
        fx(() -> {
            new javafx.animation.AnimationTimer() {
                @Override public void handle(long now) { stop(); pulse.complete(null); }
            }.start();
            return null;
        });
        pulse.get(10, TimeUnit.SECONDS);
        fx(() -> { snapshot("bookings-small.png"); return null; });
    }

    @Test void bookingDialogValidatesThenPersistsAndEditAndDeleteRefreshTable() throws Exception {
        var initialIds = fx(() -> table.getItems().stream().map(Booking::id).collect(java.util.stream.Collectors.toSet()));
        int initial = initialIds.size();
        fx(() -> {
            Platform.runLater(() -> {
                DialogPane pane = dialogPane();
                Button save = saveButton(pane);
                save.fire();
                assertFalse(((Label) pane.lookup("#errorLabel")).getText().isEmpty());
                assertTrue(pane.lookup("#customer").getStyleClass().contains("invalid"));
                @SuppressWarnings("unchecked") ComboBox<Customer> customer = (ComboBox<Customer>) pane.lookup("#customer");
                @SuppressWarnings("unchecked") ComboBox<Event> event = (ComboBox<Event>) pane.lookup("#event");
                customer.getSelectionModel().selectFirst();
                save.fire();
                assertTrue(((Label) pane.lookup("#errorLabel")).getText().contains("Выберите мероприятие"));
                assertFalse(customer.getStyleClass().contains("invalid"));
                assertTrue(event.getStyleClass().contains("invalid"));
                event.getSelectionModel().selectFirst();
                save.fire();
            });
            BookingDialogController.show(stage, context, context.services().customers().findAll(), context.services().events().findAll(),
                    null, () -> ((MainController) loader.getController()).configure(context));
            return null;
        });
        awaitLoaded();
        assertEquals(initial + 1, fx(() -> table.getItems().size()));
        Booking created = fx(() -> table.getItems().stream().filter(b -> !initialIds.contains(b.id()))
                .findFirst().orElseThrow());
        fx(() -> {
            Platform.runLater(() -> {
                DialogPane pane = dialogPane();
                @SuppressWarnings("unchecked") ComboBox<BookingStatus> status = (ComboBox<BookingStatus>) pane.lookup("#status");
                assertEquals(BookingStatus.NEW, status.getValue());
                assertTrue(pane.lookup("#customer").isDisabled());
                status.setValue(BookingStatus.PAID);
                saveButton(pane).fire();
            });
            BookingDialogController.show(stage, context, context.services().customers().findAll(), context.services().events().findAll(),
                    created, () -> ((MainController) loader.getController()).configure(context));
            return null;
        });
        awaitLoaded();
        assertEquals(BookingStatus.PAID, context.services().bookings().findById(created.id()).status());
        fx(() -> {
            table.getSelectionModel().select(table.getItems().stream().filter(b -> b.id().equals(created.id())).findFirst().orElseThrow());
            Platform.runLater(() -> {
                DialogPane pane = dialogPane();
                assertTrue(pane.getHeaderText().contains(created.ticketNumber()));
                ((Button) pane.lookupButton(ButtonType.OK)).fire();
            });
            ((Button) element("deleteButton")).fire();
            return null;
        });
        awaitLoaded();
        assertEquals(initial, fx(() -> table.getItems().size()));
        assertThrows(ru.ticketpoint.exception.EntityNotFoundException.class, () -> context.services().bookings().findById(created.id()));
    }

    @Test void operationRemainsBusyUntilFollowUpRefreshFinishes() throws Exception {
        CompletableFuture<Void> refreshStarted = new CompletableFuture<>();
        CompletableFuture<Void> completed = new CompletableFuture<>();
        CountDownLatch allowRefresh = new CountDownLatch(1);
        fx(() -> {
            var operations = new UiOperations(context.tasks(), (javafx.scene.Node) element("workspace"),
                    (Label) element("operationStatus"), (ProgressIndicator) element("progress"));
            operations.run(() -> null, "Удаление…", ignored -> operations.run(() -> {
                refreshStarted.complete(null);
                if (!allowRefresh.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Refresh was not released");
                }
                return null;
            }, "Обновление…", result -> {
                operations.message("Данные обновлены");
                completed.complete(null);
            }));
            return null;
        });
        try {
            refreshStarted.get(10, TimeUnit.SECONDS);
            assertTrue(fx(() -> ((ProgressIndicator) element("progress")).isVisible()));
            assertTrue(fx(() -> ((javafx.scene.Node) element("workspace")).isDisabled()));
            assertEquals("Обновление…", fx(() -> ((Label) element("operationStatus")).getText()));
        } finally {
            allowRefresh.countDown();
        }
        completed.get(10, TimeUnit.SECONDS);
        assertFalse(fx(() -> ((ProgressIndicator) element("progress")).isVisible()));
        assertFalse(fx(() -> ((javafx.scene.Node) element("workspace")).isDisabled()));
        assertEquals("Данные обновлены", fx(() -> ((Label) element("operationStatus")).getText()));
    }

    @Test void separateCatalogFormsValidateAndPersistTheirOwnEntities() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String customerName = "Клиент " + suffix;
        String eventName = "Концерт " + suffix;
        int customersBefore = context.services().customers().findAll().size();
        int eventsBefore = context.services().events().findAll().size();
        fx(() -> {
            Platform.runLater(() -> {
                DialogPane pane = dialogPane();
                saveButton(pane).fire();
                assertTrue(((Label) pane.lookup("#errorLabel")).getText().contains("обязательно"));
                assertTrue(pane.lookup("#name").getStyleClass().contains("invalid"));
                ((TextField) pane.lookup("#name")).setText(customerName);
                saveButton(pane).fire();
            });
            CustomerDialogController.show(stage, context, () -> ((MainController) loader.getController()).configure(context));
            return null;
        });
        awaitLoaded();
        assertEquals(customersBefore + 1, context.services().customers().findAll().size());
        assertTrue(context.services().customers().findAll().stream().anyMatch(c -> c.name().equals(customerName)));
        fx(() -> {
            Platform.runLater(() -> {
                DialogPane pane = dialogPane();
                saveButton(pane).fire();
                assertTrue(((Label) pane.lookup("#errorLabel")).getText().contains("дату"));
                ((TextField) pane.lookup("#name")).setText(eventName);
                ((TextField) pane.lookup("#venue")).setText("Тестовый зал");
                ((DatePicker) pane.lookup("#day")).setValue(LocalDate.now().plusDays(7));
                ((TextField) pane.lookup("#time")).setText("неверно");
                ((TextField) pane.lookup("#price")).setText("1500,00");
                saveButton(pane).fire();
                assertTrue(((Label) pane.lookup("#errorLabel")).getText().contains("HH:mm"));
                assertTrue(pane.lookup("#time").getStyleClass().contains("invalid"));
                ((TextField) pane.lookup("#time")).setText("19:30:45");
                saveButton(pane).fire();
                assertTrue(((Label) pane.lookup("#errorLabel")).getText().contains("HH:mm"));
                ((TextField) pane.lookup("#time")).setText("19:30");
                ((TextField) pane.lookup("#price")).setText("-1");
                saveButton(pane).fire();
                assertTrue(((Label) pane.lookup("#errorLabel")).getText().contains("Цена"));
                assertFalse(pane.lookup("#time").getStyleClass().contains("invalid"));
                assertTrue(pane.lookup("#price").getStyleClass().contains("invalid"));
                ((TextField) pane.lookup("#price")).setText("1500,001");
                saveButton(pane).fire();
                assertTrue(((Label) pane.lookup("#errorLabel")).getText().contains("двух знаков"));
                ((TextField) pane.lookup("#price")).setText("1500,00");
                saveButton(pane).fire();
            });
            EventDialogController.show(stage, context, () -> ((MainController) loader.getController()).configure(context));
            return null;
        });
        awaitLoaded();
        assertEquals(eventsBefore + 1, context.services().events().findAll().size());
        Event created = context.services().events().findAll().stream().filter(e -> e.title().equals(eventName)).findFirst().orElseThrow();
        assertEquals(new java.math.BigDecimal("1500.00"), created.price());
        assertEquals(java.time.LocalTime.of(19, 30), created.startsAt().toLocalTime());
    }

    private static DialogPane dialogPane() {
        return Window.getWindows().stream().filter(Window::isShowing).map(w -> w.getScene().getRoot())
                .filter(DialogPane.class::isInstance).map(DialogPane.class::cast).findFirst().orElseThrow();
    }

    private static Button saveButton(DialogPane pane) {
        ButtonType type = pane.getButtonTypes().stream().filter(t -> "Сохранить".equals(t.getText())).findFirst().orElseThrow();
        return (Button) pane.lookupButton(type);
    }

    private void snapshot(String name) throws Exception {
        var image = stage.getScene().snapshot(null);
        BufferedImage pixels = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < pixels.getHeight(); y++) {
            for (int x = 0; x < pixels.getWidth(); x++) pixels.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        }
        Path directory = Path.of("target", "fx-screenshots");
        Files.createDirectories(directory);
        ImageIO.write(pixels, "png", directory.resolve(name).toFile());
    }
}
