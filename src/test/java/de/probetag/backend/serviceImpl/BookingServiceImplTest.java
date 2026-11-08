package de.probetag.backend.serviceImpl;

import de.probetag.backend.entity.Booking;
import de.probetag.backend.entity.BookingStatus;
import de.probetag.backend.entity.Company;
import de.probetag.backend.entity.Participant;
import de.probetag.backend.entity.Slot;
import de.probetag.backend.entity.User;
import de.probetag.backend.event.BookingCancelledEvent;
import de.probetag.backend.exception.BadRequestException;
import de.probetag.backend.exception.ForbiddenException;
import de.probetag.backend.repository.BookingRepository;
import de.probetag.backend.repository.CompanyRepository;
import de.probetag.backend.repository.ParticipantRepository;
import de.probetag.backend.repository.SlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private SlotRepository slotRepository;
    @Mock private ParticipantRepository participantRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private BookingServiceImpl bookingService;

    private final UUID annaUserId = UUID.randomUUID();
    private Participant anna;

    @BeforeEach
    void setUp() {
        anna = new Participant();
        anna.setId(UUID.randomUUID());
        anna.setFirstName("Anna");
        anna.setLastName("Muster");
    }

    // ---------- Stornieren ----------

    @Test
    @DisplayName("Stornieren 2 Stunden vor Beginn wird abgelehnt")
    void cancelLessThan24HoursBeforeStartIsRejected() {
        Booking booking = bookingOf(anna, Instant.now().plus(2, ChronoUnit.HOURS));
        givenLoggedIn(anna);
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancel(annaUserId, booking.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("24 Stunden");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("Stornieren 3 Tage vor Beginn klappt und benachrichtigt das Unternehmen")
    void cancelInTimeSucceeds() {
        Booking booking = bookingOf(anna, Instant.now().plus(3, ChronoUnit.DAYS));
        givenLoggedIn(anna);
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        bookingService.cancel(annaUserId, booking.getId());

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(eventPublisher).publishEvent(any(BookingCancelledEvent.class));
    }

    @Test
    @DisplayName("Eine fremde Buchung kann nicht storniert werden")
    void cancelForeignBookingIsForbidden() {
        Participant ben = new Participant();
        ben.setId(UUID.randomUUID());
        Booking bensBooking = bookingOf(ben, Instant.now().plus(3, ChronoUnit.DAYS));
        givenLoggedIn(anna);
        when(bookingRepository.findById(bensBooking.getId())).thenReturn(Optional.of(bensBooking));

        assertThatThrownBy(() -> bookingService.cancel(annaUserId, bensBooking.getId()))
                .isInstanceOf(ForbiddenException.class);

        assertThat(bensBooking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    // ---------- No-Show-Sperre ----------

    @Test
    @DisplayName("Nach 3 No-Shows wird die Buchung abgelehnt, ohne den Termin zu sperren")
    void participantWithThreeNoShowsCannotBook() {
        givenLoggedIn(anna);
        when(bookingRepository.countByParticipantAndStatusSince(
                eq(anna.getId()), eq(BookingStatus.NO_SHOW), any(Instant.class)))
                .thenReturn(3L);

        assertThatThrownBy(() -> bookingService.book(annaUserId, UUID.randomUUID()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("nicht erschienen");

        verify(slotRepository, never()).findByIdForUpdate(any());
    }

    // ---------- Hilfsmethoden ----------

    private void givenLoggedIn(Participant participant) {
        when(participantRepository.findByUserId(annaUserId)).thenReturn(Optional.of(participant));
    }

    private Booking bookingOf(Participant participant, Instant startsAt) {
        User companyUser = new User();
        companyUser.setEmail("firma@test.de");

        Company company = new Company();
        company.setId(UUID.randomUUID());
        company.setName("Testfirma");
        company.setUser(companyUser);

        Slot slot = new Slot();
        slot.setId(UUID.randomUUID());
        slot.setCompany(company);
        slot.setJobTitle("Elektriker/in");
        slot.setStartsAt(startsAt);
        slot.setEndsAt(startsAt.plus(7, ChronoUnit.HOURS));
        slot.setCapacity(3);

        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setSlot(slot);
        booking.setParticipant(participant);
        booking.setStatus(BookingStatus.CONFIRMED);
        return booking;
    }
}