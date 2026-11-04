package de.probetag.backend.serviceImpl;

import de.probetag.backend.dto.SlotRequest;
import de.probetag.backend.dto.SlotResponse;
import de.probetag.backend.entity.Company;
import de.probetag.backend.entity.Slot;
import de.probetag.backend.entity.SlotStatus;
import de.probetag.backend.exception.BadRequestException;
import de.probetag.backend.exception.ForbiddenException;
import de.probetag.backend.exception.ResourceNotFoundException;
import de.probetag.backend.repository.CompanyRepository;
import de.probetag.backend.repository.SlotRepository;
import de.probetag.backend.service.SlotService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SlotServiceImpl implements SlotService {

    private final SlotRepository slotRepository;
    private final CompanyRepository companyRepository;

    @Override
    @Transactional
    public SlotResponse create(UUID userId, SlotRequest request) {
        validateTimes(request);
        Company company = getCompanyOfUser(userId);

        Slot slot = new Slot();
        slot.setCompany(company);
        applyRequest(slot, request);

        return SlotResponse.from(slotRepository.save(slot));
    }

    @Override
    @Transactional
    public SlotResponse update(UUID userId, UUID slotId, SlotRequest request) {
        validateTimes(request);
        Slot slot = getOwnSlot(userId, slotId);

        if (slot.getStatus() == SlotStatus.CANCELLED) {
            throw new BadRequestException("Ein abgesagter Termin kann nicht geändert werden");
        }
        applyRequest(slot, request);

        return SlotResponse.from(slot);
    }

    @Override
    @Transactional
    public SlotResponse cancel(UUID userId, UUID slotId) {
        Slot slot = getOwnSlot(userId, slotId);
        slot.setStatus(SlotStatus.CANCELLED);
        return SlotResponse.from(slot);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SlotResponse> findMySlots(UUID userId) {
        Company company = getCompanyOfUser(userId);
        return slotRepository.findByCompanyIdOrderByStartsAtAsc(company.getId())
                .stream()
                .map(SlotResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SlotResponse findById(UUID slotId) {
        return SlotResponse.from(getSlot(slotId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SlotResponse> search(String city, String sector, Instant from) {
        Instant start = (from != null) ? from : Instant.now();
        return slotRepository.search(SlotStatus.OPEN, start, normalize(city), normalize(sector))
                .stream()
                .map(SlotResponse::from)
                .toList();
    }

    // ---------- Hilfsmethoden ----------

    private void validateTimes(SlotRequest request) {
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new BadRequestException("Das Ende muss nach dem Beginn liegen");
        }
    }

    private void applyRequest(Slot slot, SlotRequest request) {
        slot.setJobTitle(request.jobTitle().trim());
        slot.setDescription(request.description().trim());
        slot.setStartsAt(request.startsAt());
        slot.setEndsAt(request.endsAt());
        slot.setCapacity(request.capacity());
        slot.setDressCode(request.dressCode());
    }

    private Company getCompanyOfUser(UUID userId) {
        return companyRepository.findByUserId(userId)
                .orElseThrow(() -> new ForbiddenException("Nur Unternehmen können Termine verwalten"));
    }

    private Slot getSlot(UUID slotId) {
        return slotRepository.findById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Termin nicht gefunden"));
    }

    private Slot getOwnSlot(UUID userId, UUID slotId) {
        Company company = getCompanyOfUser(userId);
        Slot slot = getSlot(slotId);
        if (!slot.getCompany().getId().equals(company.getId())) {
            throw new ForbiddenException("Dieser Termin gehört nicht zu deinem Unternehmen");
        }
        return slot;
    }

    private static String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}