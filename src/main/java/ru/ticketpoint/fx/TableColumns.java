package ru.ticketpoint.fx;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import java.time.LocalDateTime;
import java.util.function.Function;

final class TableColumns {
    private TableColumns() { }
    static <T, V> TableColumn<T, V> column(String name, double width, Function<T, V> value) {
        TableColumn<T, V> column = new TableColumn<>(name);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(value.apply(cell.getValue())));
        return column;
    }


    static <T> TableColumn<T, LocalDateTime> date(String name, Function<T, LocalDateTime> value) {
        TableColumn<T, LocalDateTime> column = column(name, 170, value);
        column.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(LocalDateTime date, boolean empty) {
                super.updateItem(date, empty);
                setText(empty || date == null ? null : UiFormat.date(date));
            }
        });
        return column;
    }
}
