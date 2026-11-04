package de.probetag.backend.service;

import de.probetag.backend.dto.SlotRequest;
import de.probetag.backend.dto.SlotResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SlotService {

    SlotResponse create(UUID userId, SlotRequest request);

    SlotResponse update(UUID userId, UUID slotId, SlotRequest request);

    SlotResponse cancel(UUID userId, UUID slotId);

    List<SlotResponse> findMySlots(UUID userId);

    SlotResponse findById(UUID slotId);

    List<SlotResponse> search(String city, String sector, Instant from);
}