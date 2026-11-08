package de.probetag.backend;

import de.probetag.backend.dto.AuthResponse;
import de.probetag.backend.dto.BookingResponse;
import de.probetag.backend.dto.RegisterCompanyRequest;
import de.probetag.backend.dto.RegisterParticipantRequest;
import de.probetag.backend.dto.SlotRequest;
import de.probetag.backend.dto.SlotResponse;
import de.probetag.backend.entity.BookingStatus;
import de.probetag.backend.entity.Company;
import de.probetag.backend.exception.ConflictException;
import de.probetag.backend.repository.BookingRepository;
import de.probetag.backend.repository.CompanyRepository;
import de.probetag.backend.service.AuthService;
import de.probetag.backend.service.BookingService;
import de.probetag.backend.service.SlotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class BookingServiceIntegrationTest {

    @Autowired private AuthService authService;
    @Autowired private SlotService slotService;
    @Autowired private BookingService bookingService;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private BookingRepository bookingRepository;

    private UUID companyUserId;

    /** Läuft vor JEDEM Test: ein neues, freigeschaltetes Unternehmen. */
    @BeforeEach
    void setUp() {
        String id = shortId();
        AuthResponse company = authService.registerCompany(new RegisterCompanyRequest(
                "firma-" + id + "@test.de", "geheim123", "Testfirma " + id, "Handwerk",
                "Teststr. 1", "Freiburg", "79098", null));
        companyUserId = company.userId();

        Company entity = companyRepository.findByUserId(companyUserId).orElseThrow();
        entity.setVerified(true);
        companyRepository.save(entity);
    }

    @Test
    @DisplayName("Eine Teilnehmerin kann einen freien Termin buchen")
    void bookingSucceeds() {
        SlotResponse slot = createSlot(2);
        UUID anna = newParticipant();

        BookingResponse booking = bookingService.book(anna, slot.id());

        assertThat(booking.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.slotId()).isEqualTo(slot.id());
    }

    @Test
    @DisplayName("Dieselbe Teilnehmerin kann einen Termin nicht zweimal buchen")
    void sameParticipantCannotBookTwice() {
        SlotResponse slot = createSlot(2);
        UUID anna = newParticipant();
        bookingService.book(anna, slot.id());

        assertThatThrownBy(() -> bookingService.book(anna, slot.id()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("bereits gebucht");
    }

    @Test
    @DisplayName("Ein voller Termin lehnt weitere Buchungen ab")
    void fullSlotRejectsFurtherBookings() {
        SlotResponse slot = createSlot(1);
        bookingService.book(newParticipant(), slot.id());

        assertThatThrownBy(() -> bookingService.book(newParticipant(), slot.id()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ausgebucht");
    }

    @Test
    @DisplayName("Eine Stornierung gibt den Platz wieder frei")
    void cancellationFreesTheSpot() {
        SlotResponse slot = createSlot(1);
        UUID anna = newParticipant();
        BookingResponse annasBooking = bookingService.book(anna, slot.id());

        bookingService.cancel(anna, annasBooking.id());
        BookingResponse bensBooking = bookingService.book(newParticipant(), slot.id());

        assertThat(bensBooking.status()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    @DisplayName("Überbuchungsschutz: 10 gleichzeitige Buchungen auf 1 Platz -> genau 1 Erfolg")
    void lastSpotCanOnlyBeBookedOnceUnderConcurrency() throws Exception {
        // Arrange: 1 Platz, 10 Teilnehmer
        SlotResponse slot = createSlot(1);
        List<UUID> participants = IntStream.range(0, 10).mapToObj(i -> newParticipant()).toList();

        ExecutorService pool = Executors.newFixedThreadPool(participants.size());
        CountDownLatch startSignal = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();

        // Act: alle Threads warten am Startsignal ...
        for (UUID userId : participants) {
            results.add(pool.submit(() -> {
                startSignal.await();
                try {
                    bookingService.book(userId, slot.id());
                    return true;
                } catch (ConflictException e) {
                    return false;
                }
            }));
        }
        startSignal.countDown();   // ... und laufen jetzt GLEICHZEITIG los

        int successes = 0;
        for (Future<Boolean> result : results) {
            if (result.get(30, TimeUnit.SECONDS)) {
                successes++;
            }
        }
        pool.shutdown();

        // Assert: genau 1 Buchung, auch in der Datenbank
        assertThat(successes).isEqualTo(1);
        assertThat(bookingRepository.countBySlotIdAndStatus(slot.id(), BookingStatus.CONFIRMED)).isEqualTo(1);
    }

    // ---------- Hilfsmethoden ----------

    private SlotResponse createSlot(int capacity) {
        Instant start = Instant.now().plus(7, ChronoUnit.DAYS);
        return slotService.create(companyUserId, new SlotRequest(
                "Elektriker/in", "Testtermin", start, start.plus(7, ChronoUnit.HOURS), capacity, null));
    }

    private UUID newParticipant() {
        String id = shortId();
        return authService.registerParticipant(new RegisterParticipantRequest(
                "teilnehmer-" + id + "@test.de", "geheim123", "Test", "Person", null, null)).userId();
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}