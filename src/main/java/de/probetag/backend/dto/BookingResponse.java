package de.probetag.backend.dto;

import de.probetag.backend.entity.Booking;
import de.probetag.backend.entity.BookingStatus;

import java.time.Instant;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        BookingStatus status,
        Instant bookedAt,
        UUID slotId,
        String jobTitle,
        Instant startsAt,
        Instant endsAt,
        String dressCode,
        String companyName,
        String address,
        String city
) {

    public static BookingResponse from(Booking booking) {
        var slot = booking.getSlot();
        var company = slot.getCompany();
        return new BookingResponse(
                booking.getId(),
                booking.getStatus(),
                booking.getCreatedAt(),
                slot.getId(),
                slot.getJobTitle(),
                slot.getStartsAt(),
                slot.getEndsAt(),
                slot.getDressCode(),
                company.getName(),
                company.getAddress(),
                company.getCity());
    }
}