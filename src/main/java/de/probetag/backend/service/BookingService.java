package de.probetag.backend.service;

import de.probetag.backend.dto.BookingResponse;
import de.probetag.backend.dto.CompanyBookingResponse;

import java.util.List;
import java.util.UUID;

public interface BookingService {

    BookingResponse book(UUID userId, UUID slotId);

    List<BookingResponse> findMyBookings(UUID userId);

    BookingResponse cancel(UUID userId, UUID bookingId);

    List<CompanyBookingResponse> findBookingsOfSlot(UUID userId, UUID slotId);

    CompanyBookingResponse markAttendance(UUID userId, UUID bookingId, boolean attended);
}