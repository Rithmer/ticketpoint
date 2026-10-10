package ru.ticketpoint.fx;

import org.jetbrains.annotations.NotNull;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;

final class UiResources {
    private UiResources() { }

    static @NotNull URL location(String name) throws FileNotFoundException {
        URL location = UiResources.class.getResource(name);
        if (location == null) {
            throw new FileNotFoundException("Не найден ресурс интерфейса: " + name);
        }
        return location;
    }

    static <C> View<C> load(String name, Class<C> controllerType) throws IOException {
        FXMLLoader loader = new FXMLLoader(location(name));
        Parent root = loader.load();
        C controller = controllerType.cast(loader.getController());
        if (controller == null) {
            throw new IOException("Не задан контроллер интерфейса: " + name);
        }
        return new View<>(root, controller);
    }

    record View<C>(Parent root, C controller) { }

    static String stylesheet() throws FileNotFoundException {
        return location("ticketpoint.css").toExternalForm();
    }
}
