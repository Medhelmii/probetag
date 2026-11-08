package de.probetag.backend.serviceImpl;

import de.probetag.backend.dto.SlotBookingCount;
import de.probetag.backend.dto.SlotRequest;
import de.probetag.backend.dto.SlotResponse;
import de.probetag.backend.entity.BookingStatus;
import de.probetag.backend.entity.Company;
import de.probetag.backend.entity.Slot;
import de.probetag.backend.entity.SlotStatus;
import de.probetag.backend.event.SlotCancelledEvent;
import de.probetag.backend.exception.BadRequestException;
import de.probetag.backend.exception.ForbiddenException;
import de.probetag.backend.exception.ResourceNotFoundException;
import de.probetag.backend.repository.BookingRepository;
import de.probetag.backend.repository.CompanyRepository;
import de.probetag.backend.repository.SlotRepository;
import de.probetag.backend.service.SlotService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SlotServiceImpl implements SlotService {

    private final SlotRepository slotRepository;
    private final CompanyRepository companyRepository;
    private final BookingRepository bookingRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public SlotResponse create(UUID userId, SlotRequest request) {
        validateTimes(request);
        Company company = getCompanyOfUser(userId);

        Slot slot = new Slot();
        slot.setCompany(company);
        applyRequest(slot, request);

        return toResponse(slotRepository.save(slot));
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

        return  toResponse(slot);
    }

    @Override
    @Transactional
    public SlotResponse cancel(UUID userId, UUID slotId) {
        Slot slot = getOwnSlot(userId, slotId);
        slot.setStatus(SlotStatus.CANCELLED);

        List<SlotCancelledEvent.Recipient> recipients = bookingRepository.findBySlotIdOrderByCreatedAtAsc(slotId)
                .stream()
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                .peek(b -> b.setStatus(BookingStatus.CANCELLED))
                .map(b -> new SlotCancelledEvent.Recipient(
                        b.getParticipant().getUser().getEmail(),
                        b.getParticipant().getFirstName()))
                .toList();

        if (!recipients.isEmpty()) {
            eventPublisher.publishEvent(new SlotCancelledEvent(
                    recipients, slot.getCompany().getName(), slot.getJobTitle(), slot.getStartsAt()));
        }

        return toResponse(slot);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SlotResponse> findMySlots(UUID userId) {
        Company company = getCompanyOfUser(userId);
        return toResponses(slotRepository.findByCompanyIdOrderByStartsAtAsc(company.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public SlotResponse findById(UUID slotId) {
        Slot slot = getSlot(slotId);
        if (!slot.getCompany().isPublic()) {
            throw new ResourceNotFoundException("Termin nicht gefunden");
        }
        return toResponse(slot);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SlotResponse> search(String city, String sector, Instant from) {
        Instant start = (from != null) ? from : Instant.now();
        return toResponses(slotRepository.search(SlotStatus.OPEN, start, normalize(city), normalize(sector)));
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
    private SlotResponse toResponse(Slot slot) {
        long booked = bookingRepository.countBySlotIdAndStatus(slot.getId(), BookingStatus.CONFIRMED);
        return SlotResponse.from(slot, booked);
    }

    private List<SlotResponse> toResponses(List<Slot> slots) {
        if (slots.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = slots.stream().map(Slot::getId).toList();
        Map<UUID, Long> counts = bookingRepository.countByStatusForSlots(ids, BookingStatus.CONFIRMED)
                .stream()
                .collect(Collectors.toMap(SlotBookingCount::slotId, SlotBookingCount::count));

        return slots.stream()
                .map(slot -> SlotResponse.from(slot, counts.getOrDefault(slot.getId(), 0L)))
                .toList();
    }
}