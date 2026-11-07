package de.probetag.backend.event;

import java.time.Instant;

public record BookingCancelledEvent(
        String companyEmail,
        String companyName,
        String participantName,
        String jobTitle,
        Instant startsAt
) {
}