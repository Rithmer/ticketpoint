package ru.ticketpoint.backend.service;

import java.util.*;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import ru.ticketpoint.backend.dto.*;
import ru.ticketpoint.backend.error.ApiException;

@Service
public class BulkService {
    private final BookingApplicationService bookings;

    public BulkService(BookingApplicationService bookings) {
        this.bookings = bookings;
    }


    public Views.BulkResult apply(Requests.Bulk req) {
        if (req.action() == Requests.BulkAction.SET_STATUS && req.status() == null)
            throw ApiException.bad("Выберите статус бронирования.");
        if (req.action() == Requests.BulkAction.ADD_TAG
                || req.action() == Requests.BulkAction.REMOVE_TAG) BookingApplicationService.tag(req.tag());
        var failures = new ArrayList<Views.Failure>();
        int ok = 0;
        for (var id : req.ids()) {
            try {
                bookings.bulkOne(id, req);
                ok++;
            } catch (ApiException e) {
                failures.add(new Views.Failure(id, e.getMessage()));
            } catch (DataAccessException e) {
                failures.add(new Views.Failure(id, "Конфликт изменения записи"));
            }
        }
        return new Views.BulkResult(req.ids().size(), ok, failures);
    }
}
