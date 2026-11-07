package de.probetag.backend.event;

import java.time.Instant;

public record BookingConfirmedEvent(
        String participantEmail,
        String participantFirstName,
        String participantLastName,
        String companyEmail,
        String companyName,
        String jobTitle,
        Instant startsAt,
        String address,
        String city,
        String dressCode
) {
}