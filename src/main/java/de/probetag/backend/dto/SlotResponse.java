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
        String dressCode,
        SlotStatus status,
        UUID companyId,
        String companyName,
        String sector,
        String address,
        String city
) {

    public static SlotResponse from(Slot slot) {
        var company = slot.getCompany();
        return new SlotResponse(
                slot.getId(),
                slot.getJobTitle(),
                slot.getDescription(),
                slot.getStartsAt(),
                slot.getEndsAt(),
                slot.getCapacity(),
                slot.getDressCode(),
                slot.getStatus(),
                company.getId(),
                company.getName(),
                company.getSector(),
                company.getAddress(),
                company.getCity());
    }
}