package de.probetag.backend.controller;

import de.probetag.backend.dto.AttendanceRequest;
import de.probetag.backend.dto.BookingResponse;
import de.probetag.backend.dto.CompanyBookingResponse;
import de.probetag.backend.security.AuthenticatedUser;
import de.probetag.backend.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    // ---------- Teilnehmerin ----------

    @PostMapping("/slots/{slotId}/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PARTICIPANT')")
    public BookingResponse book(@AuthenticationPrincipal AuthenticatedUser user,
                                @PathVariable UUID slotId) {
        return bookingService.book(user.id(), slotId);
    }

    @GetMapping("/participants/me/bookings")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public List<BookingResponse> myBookings(@AuthenticationPrincipal AuthenticatedUser user) {
        return bookingService.findMyBookings(user.id());
    }

    @PostMapping("/bookings/{id}/cancel")
    @PreAuthorize("hasRole('PARTICIPANT')")
    public BookingResponse cancel(@AuthenticationPrincipal AuthenticatedUser user,
                                  @PathVariable UUID id) {
        return bookingService.cancel(user.id(), id);
    }

    // ---------- Unternehmen ----------

    @GetMapping("/companies/me/slots/{slotId}/bookings")
    @PreAuthorize("hasRole('COMPANY')")
    public List<CompanyBookingResponse> bookingsOfSlot(@AuthenticationPrincipal AuthenticatedUser user,
                                                       @PathVariable UUID slotId) {
        return bookingService.findBookingsOfSlot(user.id(), slotId);
    }

    @PostMapping("/bookings/{id}/attendance")
    @PreAuthorize("hasRole('COMPANY')")
    public CompanyBookingResponse markAttendance(@AuthenticationPrincipal AuthenticatedUser user,
                                                 @PathVariable UUID id,
                                                 @Valid @RequestBody AttendanceRequest request) {
        return bookingService.markAttendance(user.id(), id, request.attended());
    }
}