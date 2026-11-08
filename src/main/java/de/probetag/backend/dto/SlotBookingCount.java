package de.probetag.backend.dto;

import java.util.UUID;

public record SlotBookingCount(UUID slotId, long count) {
}