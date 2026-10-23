package ru.ticketpoint.export;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import ru.ticketpoint.model.Booking;

public interface BookingExporter {
    void export(List<Booking> bookings, Path target) throws IOException;
}
