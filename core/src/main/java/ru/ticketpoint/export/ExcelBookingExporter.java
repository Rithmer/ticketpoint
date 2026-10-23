package ru.ticketpoint.export;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import ru.ticketpoint.model.Booking;

public final class ExcelBookingExporter implements BookingExporter {
    private static final List<String> HEADINGS = List.of("ID", "Номер билета", "Код клиента", "Клиент",
            "Мероприятие", "Место", "Дата мероприятия", "Цена при бронировании, руб.", "Статус");
    private static final int[] WIDTHS = {10, 43, 43, 27, 35, 35, 23, 17, 16};

    @Override
    public void export(List<Booking> bookings, Path target) throws IOException {
        List<Booking> snapshot = validatedSnapshot(bookings);
        Path destination = target.toAbsolutePath();
        Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(destination.getParent(), ".bookings-", ".xlsx");
        Throwable failure = null;
        try {
            writeWorkbook(snapshot, temporary);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException | Error e) {
            failure = e;
            throw e;
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException | RuntimeException cleanupError) {
                if (failure != null) {
                    failure.addSuppressed(cleanupError);
                } else {
                    throw cleanupError;
                }
            }
        }
    }

    private static List<Booking> validatedSnapshot(List<Booking> bookings) {
        List<Booking> snapshot = List.copyOf(bookings);
        if (snapshot.size() >= SpreadsheetVersion.EXCEL2007.getMaxRows()) {
            throw new IllegalArgumentException("Слишком много бронирований для одного листа Excel");
        }
        for (Booking booking : snapshot) {
            int year = booking.event().startsAt().getYear();
            if (year < 1900 || year > 9999) {
                throw new IllegalArgumentException("Дата мероприятия для Excel должна быть в диапазоне 1900–9999 годов");
            }
        }
        return snapshot;
    }

    private static void writeWorkbook(List<Booking> bookings, Path target) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Бронирования");
            CellStyle text = textStyle(workbook);
            CellStyle header = headerStyle(workbook, text);
            CellStyle price = workbook.createCellStyle();
            price.cloneStyleFrom(text);
            price.setDataFormat(workbook.createDataFormat().getFormat("0.00"));
            CellStyle date = workbook.createCellStyle();
            date.cloneStyleFrom(text);
            date.setDataFormat(workbook.createDataFormat().getFormat("dd.mm.yyyy hh:mm"));

            var headings = sheet.createRow(0);
            headings.setHeightInPoints(32);
            for (int column = 0; column < HEADINGS.size(); column++) {
                var cell = headings.createCell(column);
                cell.setCellValue(HEADINGS.get(column));
                cell.setCellStyle(header);
                sheet.setColumnWidth(column, WIDTHS[column] * 256);
            }
            int rowIndex = 1;
            for (Booking booking : bookings) {
                var row = sheet.createRow(rowIndex++);
                row.setHeightInPoints(38);
                String[] values = {String.valueOf(booking.id()), booking.ticketNumber(),
                        booking.customer().customerCode(), booking.customer().name(),
                        booking.event().title(), booking.event().venue()};
                for (int column = 0; column < values.length; column++) {
                    var cell = row.createCell(column);
                    cell.setCellValue(values[column]);
                    cell.setCellStyle(text);
                }
                var dateCell = row.createCell(6);
                dateCell.setCellValue(booking.event().startsAt());
                dateCell.setCellStyle(date);
                var priceCell = row.createCell(7);
                priceCell.setCellValue(booking.priceAtBooking().doubleValue());
                priceCell.setCellStyle(price);
                var statusCell = row.createCell(8);
                statusCell.setCellValue(booking.status().name());
                statusCell.setCellStyle(text);
            }
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new CellRangeAddress(0, rowIndex - 1, 0, HEADINGS.size() - 1));
            try (var output = Files.newOutputStream(target)) {
                workbook.write(output);
            }
        }
    }

    private static CellStyle textStyle(XSSFWorkbook workbook) {
        var font = workbook.createFont();
        font.setFontName("Calibri");
        font.setFontHeightInPoints((short) 11);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        return style;
    }

    private static CellStyle headerStyle(XSSFWorkbook workbook, CellStyle text) {
        var font = workbook.createFont();
        font.setFontName("Calibri");
        font.setFontHeightInPoints((short) 11);
        font.setBold(true);
        font.setColor(new XSSFColor(new byte[]{(byte) 255, (byte) 255, (byte) 255}, null));
        XSSFCellStyle style = workbook.createCellStyle();
        style.cloneStyleFrom(text);
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(new byte[]{0x23, 0x4a, 0x6f}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }
}
