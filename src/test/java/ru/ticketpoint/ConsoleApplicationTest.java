package ru.ticketpoint;

import org.junit.jupiter.api.Test;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Scanner;
import static org.junit.jupiter.api.Assertions.*;

class ConsoleApplicationTest {
    @Test void helpWorksWithoutConnectingToDatabase() {
        StringWriter output = new StringWriter();
        StringWriter errors = new StringWriter();
        int code = ConsoleApplication.run(new String[]{"--help"}, new Scanner(""),
                new PrintWriter(output), new PrintWriter(errors));
        assertEquals(0, code);
        assertTrue(output.toString().contains("--check"));
        assertTrue(output.toString().contains("--export"));
        assertEquals("", errors.toString());
    }

    @Test void malformedArgumentsReturnUsageAndExitCodeTwo() {
        for (String[] args : new String[][]{{"unknown"}, {"--export"}, {"--export", " "}, {"--check", "extra"}}) {
            StringWriter errors = new StringWriter();
            int code = ConsoleApplication.run(args, new Scanner(""), new PrintWriter(new StringWriter()), new PrintWriter(errors));
            assertEquals(2, code);
            assertTrue(errors.toString().contains("--export"));
        }
    }
}
