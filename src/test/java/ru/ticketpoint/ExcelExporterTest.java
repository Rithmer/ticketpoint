package ru.ticketpoint;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.ticketpoint.model.Booking;
import ru.ticketpoint.model.BookingStatus;
import ru.ticketpoint.model.Customer;
import ru.ticketpoint.model.Event;
import ru.ticketpoint.export.ExcelBookingExporter;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExcelExporterTest {
    @TempDir Path directory;
    private final ExcelBookingExporter exporter = new ExcelBookingExporter();

    @Test
    void exportsTypedCellsWithSafeText() throws Exception {
        Event event = new Event(1L, "=1+1 <&>", "Hall", LocalDateTime.of(2030, 12, 25, 19, 30), new BigDecimal("5500"));
        Booking booking = new Booking(1L, "TP-TEST",
                new Customer(1L, "CUS-12345678-1234-1234-1234-1234567890AB", "Иван"),
                event, new BigDecimal("5100.00"), BookingStatus.NEW, LocalDateTime.now());
        Path output = directory.resolve("bookings.xlsx");
        exporter.export(List.of(booking), output);
        try (var input = Files.newInputStream(output); XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            var sheet = workbook.getSheet("Бронирования");
            assertEquals(2, sheet.getPhysicalNumberOfRows());
            assertEquals(9, sheet.getRow(0).getPhysicalNumberOfCells());
            var row = sheet.getRow(1);
            assertEquals(CellType.STRING, row.getCell(0).getCellType());
            assertEquals("1", row.getCell(0).getStringCellValue());
            assertEquals("TP-TEST", row.getCell(1).getStringCellValue());
            assertEquals("CUS-12345678-1234-1234-1234-1234567890AB", row.getCell(2).getStringCellValue());
            assertEquals(CellType.STRING, row.getCell(4).getCellType());
            assertEquals("=1+1 <&>", row.getCell(4).getStringCellValue());
            assertEquals(CellType.NUMERIC, row.getCell(7).getCellType());
            assertEquals(5100.0, row.getCell(7).getNumericCellValue());
            assertEquals("0.00", row.getCell(7).getCellStyle().getDataFormatString());
            assertTrue(DateUtil.isCellDateFormatted(row.getCell(6)));
            assertEquals(event.getStartsAt(), row.getCell(6).getLocalDateTimeCellValue());
            for (var cell : row) {
                assertNotEquals(CellType.FORMULA, cell.getCellType());
            }
            assertTrue(sheet.getPaneInformation().isFreezePane());
            assertEquals(1, sheet.getPaneInformation().getHorizontalSplitPosition());
            assertEquals("A1:I2", sheet.getCTWorksheet().getAutoFilter().getRef());
        }
    }

    @Test
    void exportsEmptyResultAndDoesNotDestroyExistingFileOnInvalidInput() throws Exception {
        Path output = directory.resolve("empty.xlsx");
        exporter.export(List.of(), output);
        try (var input = Files.newInputStream(output); XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            assertEquals(1, workbook.getSheetAt(0).getPhysicalNumberOfRows());
        }
        byte[] original = Files.readAllBytes(output);
        assertThrows(NullPointerException.class, () -> exporter.export(null, output));
        assertArrayEquals(original, Files.readAllBytes(output));
    }
}
