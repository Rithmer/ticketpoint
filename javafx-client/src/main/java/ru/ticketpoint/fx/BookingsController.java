package ru.ticketpoint.fx;

import java.io.File;
import java.util.List;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import ru.ticketpoint.model.*;
import ru.ticketpoint.service.BookingFilter;

public final class BookingsController {
    @FXML
    private TableView<Booking> bookingTable;
    @FXML
    private TextField searchField;
    @FXML
    private ComboBox<BookingStatus> statusFilter;
    @FXML
    private ComboBox<Customer> customerFilter;
    @FXML
    private ComboBox<Event> eventFilter;
    @FXML
    private DatePicker dateFrom;
    @FXML
    private DatePicker dateTo;
    @FXML
    private Label filterError;
    @FXML
    private Label resultCount;
    @FXML
    private Button addButton;
    @FXML
    private Button editButton;
    @FXML
    private Button deleteButton;
    @FXML
    private Button detailsButton;
    @FXML
    private Label heading;
    private final ObservableList<Booking> source = FXCollections.observableArrayList();
    private final FilteredList<Booking> filtered = new FilteredList<>(source);
    private final SortedList<Booking> sorted = new SortedList<>(filtered);
    private AppContext context;
    private Runnable refresh;
    private UiOperations operations;
    private List<Customer> customers = List.of();
    private List<Event> events = List.of();
    private boolean updatingFilters;

    @FXML
    private void initialize() {
        bookingTable.getColumns().add(TableColumns.column("Билет", 190, Booking::ticketNumber));
        bookingTable.getColumns().add(TableColumns.column("Клиент", 170, b -> b.customer().name()));
        bookingTable.getColumns().add(TableColumns.column("Мероприятие", 210, b -> b.event().title()));
        bookingTable.getColumns().add(TableColumns.column("Место", 150, b -> b.event().venue()));
        bookingTable.getColumns().add(TableColumns.date("Дата мероприятия", b -> b.event().startsAt()));
        bookingTable.getColumns().add(TableColumns.column("Цена, ₽", 110, Booking::priceAtBooking));
        bookingTable.getColumns().add(TableColumns.column("Статус", 115, b -> UiFormat.status(b.status())));
        bookingTable.getColumns().add(TableColumns.date("Создано", Booking::createdAt));
        sorted.comparatorProperty().bind(bookingTable.comparatorProperty());
        bookingTable.setItems(sorted);
        bookingTable.setPlaceholder(new Label("Нет бронирований. Добавьте запись или измените фильтры."));
        bookingTable.getSelectionModel().selectedItemProperty().addListener((observable, old, selected) -> {
            editButton.setDisable(context == null || !context.canEdit(selected));
            deleteButton.setDisable(context == null || !context.canDelete(selected));
            detailsButton.setDisable(selected == null);
        });
        statusFilter.setConverter(UiFormat.converter(UiFormat::status, "Все статусы"));
        statusFilter.getItems().add(null);
        statusFilter.getItems().addAll(BookingStatus.values());
        customerFilter.setConverter(UiFormat.converter(UiFormat::customer, "Все клиенты"));
        eventFilter.setConverter(UiFormat.converter(UiFormat::event, "Все мероприятия"));
        searchField.textProperty().addListener((o, a, b) -> applyFilters());
        statusFilter.valueProperty().addListener((o, a, b) -> applyFilters());
        customerFilter.valueProperty().addListener((o, a, b) -> applyFilters());
        eventFilter.valueProperty().addListener((o, a, b) -> applyFilters());
        dateFrom.valueProperty().addListener((o, a, b) -> applyFilters());
        dateTo.valueProperty().addListener((o, a, b) -> applyFilters());
        filterError.managedProperty().bind(filterError.textProperty().isNotEmpty());
        filterError.visibleProperty().bind(filterError.managedProperty());
    }

    void configure(AppContext context, Runnable refresh, UiOperations operations) {
        this.context = context;
        this.refresh = refresh;
        this.operations = operations;
        addButton.setDisable(!context.canWrite() && !context.isCustomer());
        heading.setText(context.isCustomer() ? "Мои заявки и билеты" : "Заявки и билеты клиентов");
        addButton.setText(context.isCustomer() ? "+ Забронировать билет" : "+ Новое бронирование");
        customerFilter.setVisible(!context.isCustomer());
        customerFilter.setManaged(!context.isCustomer());
    }

    void populate(List<Booking> bookings, List<Customer> customers, List<Event> events) {
        Long selectedId = bookingTable.getSelectionModel().getSelectedItem() == null ? null
                : bookingTable.getSelectionModel().getSelectedItem().id();
        source.setAll(bookings);
        this.customers = List.copyOf(customers);
        this.events = List.copyOf(events);
        updatingFilters = true;
        Long customerId = customerFilter.getValue() == null ? null : customerFilter.getValue().id();
        Long eventId = eventFilter.getValue() == null ? null : eventFilter.getValue().id();
        customerFilter.getItems().setAll(customers);
        customerFilter.getItems().addFirst(null);
        eventFilter.getItems().setAll(events);
        eventFilter.getItems().addFirst(null);
        customerFilter.setValue(customers.stream().filter(c -> c.id().equals(customerId)).findFirst().orElse(null));
        eventFilter.setValue(events.stream().filter(e -> e.id().equals(eventId)).findFirst().orElse(null));
        updatingFilters = false;
        applyFilters();
        if (selectedId != null) {
            sorted.stream().filter(b -> b.id().equals(selectedId)).findFirst()
                    .ifPresent(b -> bookingTable.getSelectionModel().select(b));
        }
    }

    private void applyFilters() {
        if (updatingFilters) {
            return;
        }
        try {
            var filter = new BookingFilter(searchField.getText(), statusFilter.getValue(),
                    customerFilter.getValue() == null ? null : customerFilter.getValue().id(),
                    eventFilter.getValue() == null ? null : eventFilter.getValue().id(),
                    dateFrom.getValue(), dateTo.getValue());
            filtered.setPredicate(filter::matches);
            filterError.setText("");
        } catch (IllegalArgumentException e) {
            filterError.setText(e.getMessage());
            filtered.setPredicate(b -> false);
        }
        resultCount.setText("Показано " + filtered.size() + " из " + source.size());
    }

    @FXML
    private void resetFilters() {
        updatingFilters = true;
        searchField.clear();
        statusFilter.setValue(null);
        customerFilter.setValue(null);
        eventFilter.setValue(null);
        dateFrom.setValue(null);
        dateTo.setValue(null);
        updatingFilters = false;
        applyFilters();
    }

    @FXML
    private void addBooking() {
        BookingDialogController.show(bookingTable.getScene().getWindow(), context, customers, events, null, refresh);
    }

    @FXML
    private void refreshBookings() {
        refresh.run();
    }

    @FXML
    private void editBooking() {
        Booking booking = bookingTable.getSelectionModel().getSelectedItem();
        if (booking != null) {
            BookingDialogController.show(bookingTable.getScene().getWindow(), context, customers, events, booking, refresh);
        }
    }

    @FXML
    private void viewBooking() {
        Booking booking = bookingTable.getSelectionModel().getSelectedItem();
        if (booking != null) {
            BookingDetailsController.show(bookingTable.getScene().getWindow(), booking,
                    context.services().clock().getZone().getId());
        }
    }

    @FXML
    private void deleteBooking() {
        Booking booking = bookingTable.getSelectionModel().getSelectedItem();
        if (booking == null) {
            return;
        }
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.initOwner(bookingTable.getScene().getWindow());
        confirmation.setTitle("Удаление бронирования");
        confirmation.setHeaderText("Удалить билет " + booking.ticketNumber() + "?");
        confirmation.setContentText(booking.customer().name() + " • " + booking.event().title()
                + "\nБронирование будет убрано из списка. История сохранится в архиве, если он доступен.");
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            operations.run(() -> {
                        context.services().bookings().delete(booking.id());
                        return null;
                    },
                    "Удаление бронирования…", result -> refresh.run());
        }
    }

    @FXML
    private void exportBookings() {
        if (!filterError.getText().isEmpty()) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Экспорт текущего списка бронирований");
        chooser.setInitialFileName("bookings.xlsx");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Книга Excel (*.xlsx)", "*.xlsx"));
        File file = chooser.showSaveDialog(bookingTable.getScene().getWindow());
        if (file == null) {
            return;
        }
        List<Booking> snapshot = List.copyOf(sorted);
        operations.run(() -> {
                    context.services().exporter().export(snapshot, file.toPath());
                    return file;
                },
                "Экспорт " + snapshot.size() + " бронирований…",
                saved -> operations.message("Сохранено " + snapshot.size() + " бронирований: " + saved.getAbsolutePath()));
    }
}
