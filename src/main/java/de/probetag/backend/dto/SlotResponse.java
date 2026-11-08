package de.probetag.backend.dto;

import de.probetag.backend.entity.Slot;
import de.probetag.backend.entity.SlotStatus;

import java.time.Instant;
import java.util.UUID;

public record SlotResponse(
        UUID id,
        String jobTitle,
        String description,
        Instant startsAt,
        Instant endsAt,
        int capacity,
        int freeSpots,
        String dressCode,
        SlotStatus status,
        UUID companyId,
        String companyName,
        String sector,
        String address,
        String city
) {

    public static SlotResponse from(Slot slot, long confirmedBookings) {
        var company = slot.getCompany();
        int free = (int) Math.max(0, slot.getCapacity() - confirmedBookings);
        return new SlotResponse(
                slot.getId(),
                slot.getJobTitle(),
                slot.getDescription(),
                slot.getStartsAt(),
                slot.getEndsAt(),
                slot.getCapacity(),
                free,
                slot.getDressCode(),
                slot.getStatus(),
                company.getId(),
                company.getName(),
                company.getSector(),
                company.getAddress(),
                company.getCity());
    }
}