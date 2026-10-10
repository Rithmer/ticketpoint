package ru.ticketpoint.fx;

import javafx.application.Application;
import ru.ticketpoint.ConsoleApplication;

import java.util.Arrays;

public final class FxLauncher {
    private FxLauncher() { }

    public static void main(String[] args) {
        if (args.length > 0 && "--console".equals(args[0])) {
            ConsoleApplication.main(Arrays.copyOfRange(args, 1, args.length));
        } else if (args.length > 0 && args[0].startsWith("--")) {
            ConsoleApplication.main(args);
        } else {
            Application.launch(TicketPointApplication.class, args);
        }
    }
}
