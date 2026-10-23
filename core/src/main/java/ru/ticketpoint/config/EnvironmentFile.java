package ru.ticketpoint.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EnvironmentFile {
    private static final Pattern VARIABLE = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]*)}");

    private EnvironmentFile() {
    }

    public static Map<String, String> read(Path file, Map<String, String> environment) {
        Map<String, String> values = new HashMap<>(environment);
        if (!Files.exists(file)) {
            return values;
        }
        try {
            int number = 0;
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                number++;
                String entry = line.strip();
                if (entry.isEmpty() || entry.startsWith("#")) {
                    continue;
                }
                int separator = entry.indexOf('=');
                if (separator < 1 || !entry.substring(0, separator).matches("[A-Z][A-Z0-9_]*")) {
                    throw new IllegalArgumentException("В файле настроек неверно заполнена строка " + number
                            + ". Используйте формат ИМЯ=значение.");
                }
                String name = entry.substring(0, separator);
                String value = entry.substring(separator + 1).strip();
                boolean literal = value.length() >= 2 && value.startsWith("'") && value.endsWith("'");
                if (literal || (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\""))) {
                    value = value.substring(1, value.length() - 1);
                }
                if (environment.containsKey(name)) {
                    continue;
                }
                if (!literal) {
                    var references = VARIABLE.matcher(value);
                    StringBuilder expanded = new StringBuilder();
                    while (references.find()) {
                        String replacement = values.get(references.group(1));
                        if (replacement == null) {
                            throw new IllegalArgumentException("В файле настроек не задан параметр "
                                    + references.group(1) + ". Проверьте его значение.");
                        }
                        references.appendReplacement(expanded, Matcher.quoteReplacement(replacement));
                    }
                    references.appendTail(expanded);
                    value = expanded.toString();
                }
                values.put(name, value);
            }
        } catch (IOException error) {
            throw new IllegalArgumentException("Не удалось прочитать файл настроек. Проверьте права доступа.", error);
        }
        return values;
    }
}
