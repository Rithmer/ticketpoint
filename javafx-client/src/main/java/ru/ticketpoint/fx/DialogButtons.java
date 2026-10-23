package ru.ticketpoint.fx;

import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;

final class DialogButtons {
    static final ButtonType CONFIRM = new ButtonType("Подтвердить", ButtonBar.ButtonData.OK_DONE);
    static final ButtonType CANCEL = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
    static final ButtonType CLOSE = new ButtonType("Закрыть", ButtonBar.ButtonData.CANCEL_CLOSE);

    private DialogButtons() {
    }
}
