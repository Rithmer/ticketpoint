package ru.ticketpoint.fx;

import javafx.application.Application;

public final class FxLauncher {
    private FxLauncher() {
    }

    public static void main(String[] args) {
        Application.launch(TicketPointApplication.class, args);
    }
}
