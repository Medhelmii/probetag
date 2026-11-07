package de.probetag.backend.event;

import java.time.Instant;
import java.util.List;

public record SlotCancelledEvent(
        List<Recipient> recipients,
        String companyName,
        String jobTitle,
        Instant startsAt
) {
    public record Recipient(String email, String firstName) {
    }
}