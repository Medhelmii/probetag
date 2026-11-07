package de.probetag.backend.scheduler;

import de.probetag.backend.entity.Booking;
import de.probetag.backend.entity.BookingStatus;
import de.probetag.backend.repository.BookingRepository;
import de.probetag.backend.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderJob {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter
            .ofPattern("EEEE, dd.MM.yyyy 'um' HH:mm 'Uhr'", Locale.GERMANY)
            .withZone(BERLIN);

    private final BookingRepository bookingRepository;
    private final EmailService emailService;

    @Scheduled(cron = "${app.reminder.cron:0 0 18 * * *}", zone = "Europe/Berlin")
    @Transactional
    public void sendReminders() {
        LocalDate tomorrow = LocalDate.now(BERLIN).plusDays(1);
        Instant from = tomorrow.atStartOfDay(BERLIN).toInstant();
        Instant to = tomorrow.plusDays(1).atStartOfDay(BERLIN).toInstant();

        List<Booking> bookings = bookingRepository.findForReminder(BookingStatus.CONFIRMED, from, to);
        log.info("Erinnerungen für {}: {} Buchung(en) gefunden", tomorrow, bookings.size());

        for (Booking booking : bookings) {
            var slot = booking.getSlot();
            var company = slot.getCompany();
            var participant = booking.getParticipant();

            boolean sent = emailService.send(participant.getUser().getEmail(),
                    "Erinnerung: morgen ist dein Probetag!",
                    "Hallo " + participant.getFirstName() + ",\n\n"
                            + "morgen ist es soweit:\n\n"
                            + "Beruf:        " + slot.getJobTitle() + "\n"
                            + "Unternehmen:  " + company.getName() + "\n"
                            + "Wann:         " + TIME_FORMAT.format(slot.getStartsAt()) + "\n"
                            + "Wo:           " + company.getAddress() + ", " + company.getCity() + "\n\n"
                            + "Falls du doch nicht kommen kannst, sag bitte dem Unternehmen Bescheid.\n\n"
                            + "Viel Erfolg!\nDein Probetag-Team");

            if (sent) {
                booking.setReminderSentAt(Instant.now());
            }
        }
    }
}