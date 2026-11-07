package de.probetag.backend.serviceImpl;

import de.probetag.backend.dto.BookingResponse;
import de.probetag.backend.dto.CompanyBookingResponse;
import de.probetag.backend.entity.Booking;
import de.probetag.backend.entity.BookingStatus;
import de.probetag.backend.entity.Company;
import de.probetag.backend.entity.Participant;
import de.probetag.backend.entity.Slot;
import de.probetag.backend.entity.SlotStatus;
import de.probetag.backend.event.BookingCancelledEvent;
import de.probetag.backend.event.BookingConfirmedEvent;
import de.probetag.backend.exception.BadRequestException;
import de.probetag.backend.exception.ConflictException;
import de.probetag.backend.exception.ForbiddenException;
import de.probetag.backend.exception.ResourceNotFoundException;
import de.probetag.backend.repository.BookingRepository;
import de.probetag.backend.repository.CompanyRepository;
import de.probetag.backend.repository.ParticipantRepository;
import de.probetag.backend.repository.SlotRepository;
import de.probetag.backend.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private static final Duration CANCELLATION_DEADLINE = Duration.ofHours(24);

    private final BookingRepository bookingRepository;
    private final SlotRepository slotRepository;
    private final ParticipantRepository participantRepository;
    private final CompanyRepository companyRepository;
    private final ApplicationEventPublisher eventPublisher;

    // ---------- Teilnehmerin ----------

    @Override
    @Transactional
    public BookingResponse book(UUID userId, UUID slotId) {
        Participant participant = getParticipantOfUser(userId);

        // 1. Termin SPERREN: wer gleichzeitig bucht, wartet hier
        Slot slot = slotRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Termin nicht gefunden"));

        // 2. Ist der Termin buchbar?
        if (slot.getStatus() != SlotStatus.OPEN) {
            throw new ConflictException("Dieser Termin wurde abgesagt");
        }
        if (!slot.getStartsAt().isAfter(Instant.now())) {
            throw new ConflictException("Dieser Termin liegt in der Vergangenheit");
        }

        // 3. Schon gebucht?
        Booking booking = bookingRepository.findBySlotIdAndParticipantId(slotId, participant.getId())
                .orElse(null);
        if (booking != null && booking.getStatus() != BookingStatus.CANCELLED) {
            throw new ConflictException("Du hast diesen Termin bereits gebucht");
        }

        // 4. Noch Platz frei?
        long taken = bookingRepository.countBySlotIdAndStatus(slotId, BookingStatus.CONFIRMED);
        if (taken >= slot.getCapacity()) {
            throw new ConflictException("Dieser Termin ist leider ausgebucht");
        }

        // 5. Speichern: neue Buchung oder stornierte wieder aktivieren
        if (booking == null) {
            booking = new Booking();
            booking.setSlot(slot);
            booking.setParticipant(participant);
        }
        booking.setStatus(BookingStatus.CONFIRMED);

        Booking saved = bookingRepository.save(booking);

        Company company = slot.getCompany();
        eventPublisher.publishEvent(new BookingConfirmedEvent(
                participant.getUser().getEmail(),
                participant.getFirstName(),
                participant.getLastName(),
                company.getUser().getEmail(),
                company.getName(),
                slot.getJobTitle(),
                slot.getStartsAt(),
                company.getAddress(),
                company.getCity(),
                slot.getDressCode()));

        return BookingResponse.from(saved);
    }   // <- hier endet die Transaktion und die Sperre wird freigegeben

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> findMyBookings(UUID userId) {
        Participant participant = getParticipantOfUser(userId);
        return bookingRepository.findByParticipantIdOrderBySlotStartsAtDesc(participant.getId())
                .stream()
                .map(BookingResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public BookingResponse cancel(UUID userId, UUID bookingId) {
        Participant participant = getParticipantOfUser(userId);
        Booking booking = getBooking(bookingId);

        if (!booking.getParticipant().getId().equals(participant.getId())) {
            throw new ForbiddenException("Diese Buchung gehört nicht dir");
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ConflictException("Nur bestätigte Buchungen können storniert werden");
        }
        Instant deadline = booking.getSlot().getStartsAt().minus(CANCELLATION_DEADLINE);
        if (Instant.now().isAfter(deadline)) {
            throw new BadRequestException("Stornieren ist nur bis 24 Stunden vor Beginn möglich");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        Slot slot = booking.getSlot();
        eventPublisher.publishEvent(new BookingCancelledEvent(
                slot.getCompany().getUser().getEmail(),
                slot.getCompany().getName(),
                participant.getFirstName() + " " + participant.getLastName(),
                slot.getJobTitle(),
                slot.getStartsAt()));
        return BookingResponse.from(booking);
    }

    // ---------- Unternehmen ----------

    @Override
    @Transactional(readOnly = true)
    public List<CompanyBookingResponse> findBookingsOfSlot(UUID userId, UUID slotId) {
        Company company = getCompanyOfUser(userId);
        Slot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Termin nicht gefunden"));

        if (!slot.getCompany().getId().equals(company.getId())) {
            throw new ForbiddenException("Dieser Termin gehört nicht zu deinem Unternehmen");
        }

        return bookingRepository.findBySlotIdOrderByCreatedAtAsc(slotId)
                .stream()
                .map(CompanyBookingResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public CompanyBookingResponse markAttendance(UUID userId, UUID bookingId, boolean attended) {
        Company company = getCompanyOfUser(userId);
        Booking booking = getBooking(bookingId);
        Slot slot = booking.getSlot();

        if (!slot.getCompany().getId().equals(company.getId())) {
            throw new ForbiddenException("Diese Buchung gehört nicht zu deinem Unternehmen");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new ConflictException("Eine stornierte Buchung kann nicht bewertet werden");
        }
        if (Instant.now().isBefore(slot.getStartsAt())) {
            throw new BadRequestException("Die Anwesenheit kann erst nach Beginn des Termins markiert werden");
        }

        booking.setStatus(attended ? BookingStatus.ATTENDED : BookingStatus.NO_SHOW);
        return CompanyBookingResponse.from(booking);
    }

    // ---------- Hilfsmethoden ----------

    private Participant getParticipantOfUser(UUID userId) {
        return participantRepository.findByUserId(userId)
                .orElseThrow(() -> new ForbiddenException("Nur Teilnehmer können Termine buchen"));
    }

    private Company getCompanyOfUser(UUID userId) {
        return companyRepository.findByUserId(userId)
                .orElseThrow(() -> new ForbiddenException("Nur Unternehmen haben Zugriff"));
    }

    private Booking getBooking(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Buchung nicht gefunden"));
    }
}