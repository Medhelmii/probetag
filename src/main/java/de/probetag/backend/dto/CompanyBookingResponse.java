package de.probetag.backend.dto;

import de.probetag.backend.entity.Booking;
import de.probetag.backend.entity.BookingStatus;

import java.time.Instant;
import java.util.UUID;

public record CompanyBookingResponse(
        UUID bookingId,
        BookingStatus status,
        Instant bookedAt,
        String firstName,
        String lastName,
        String email,
        String phone,
        String motivation
) {

    public static CompanyBookingResponse from(Booking booking) {
        var participant = booking.getParticipant();
        return new CompanyBookingResponse(
                booking.getId(),
                booking.getStatus(),
                booking.getCreatedAt(),
                participant.getFirstName(),
                participant.getLastName(),
                participant.getUser().getEmail(),
                participant.getPhone(),
                participant.getMotivation());
    }
}