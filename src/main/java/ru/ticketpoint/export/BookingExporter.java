package ru.ticketpoint.export;

import ru.ticketpoint.model.Booking;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface BookingExporter {
    void export(List<Booking> bookings, Path target) throws IOException;
}
