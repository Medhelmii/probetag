package de.probetag.backend.listener;

import de.probetag.backend.event.*;
import de.probetag.backend.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class NotificationListener {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
            .ofPattern("EEEE, dd.MM.yyyy 'um' HH:mm 'Uhr'", Locale.GERMANY)
            .withZone(ZoneId.of("Europe/Berlin"));

    private final EmailService emailService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserRegistered(UserRegisteredEvent event) {
        emailService.send(event.email(),
                "Willkommen bei Probetag!",
                "Hallo " + event.firstName() + ",\n\n"
                        + "dein Konto ist eingerichtet. Du kannst jetzt Probetage suchen und buchen.\n\n"
                        + "Viel Erfolg!\nDein Probetag-Team");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        String when = format(event.startsAt());

        // 1. Bestätigung an die Teilnehmerin
        StringBuilder text = new StringBuilder()
                .append("Hallo ").append(event.participantFirstName()).append(",\n\n")
                .append("dein Probetag ist gebucht!\n\n")
                .append("Beruf:        ").append(event.jobTitle()).append("\n")
                .append("Unternehmen:  ").append(event.companyName()).append("\n")
                .append("Wann:         ").append(when).append("\n")
                .append("Wo:           ").append(event.address()).append(", ").append(event.city()).append("\n");
        if (event.dressCode() != null && !event.dressCode().isBlank()) {
            text.append("Kleidung:     ").append(event.dressCode()).append("\n");
        }
        text.append("\nFalls du nicht kommen kannst, storniere bitte bis 24 Stunden vorher.\n\n")
                .append("Viel Spaß!\nDein Probetag-Team");

        emailService.send(event.participantEmail(), "Buchung bestätigt: " + event.jobTitle(), text.toString());

        // 2. Info an das Unternehmen
        emailService.send(event.companyEmail(),
                "Neue Buchung: " + event.jobTitle(),
                "Hallo " + event.companyName() + ",\n\n"
                        + event.participantFirstName() + " " + event.participantLastName()
                        + " hat den Probetag \"" + event.jobTitle() + "\" am " + when + " gebucht.\n\n"
                        + "Alle Teilnehmer siehst du in deinem Dashboard.\n\nDein Probetag-Team");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingCancelled(BookingCancelledEvent event) {
        emailService.send(event.companyEmail(),
                "Stornierung: " + event.jobTitle(),
                "Hallo " + event.companyName() + ",\n\n"
                        + event.participantName() + " hat die Buchung für \"" + event.jobTitle()
                        + "\" am " + format(event.startsAt()) + " storniert.\n"
                        + "Der Platz ist wieder frei.\n\nDein Probetag-Team");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSlotCancelled(SlotCancelledEvent event) {
        String when = format(event.startsAt());
        for (SlotCancelledEvent.Recipient recipient : event.recipients()) {
            emailService.send(recipient.email(),
                    "Abgesagt: " + event.jobTitle(),
                    "Hallo " + recipient.firstName() + ",\n\n"
                            + "leider hat " + event.companyName() + " den Probetag \"" + event.jobTitle()
                            + "\" am " + when + " abgesagt. Deine Buchung wurde storniert.\n\n"
                            + "Schau gerne nach anderen Terminen auf Probetag.\n\nDein Probetag-Team");
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCompanyVerified(CompanyVerifiedEvent event) {
        emailService.send(event.email(),
                "Dein Unternehmen ist freigeschaltet!",
                "Hallo " + event.companyName() + ",\n\n"
                        + "wir haben dein Profil geprüft und freigeschaltet. "
                        + "Deine Probetage sind ab jetzt für alle sichtbar.\n\n"
                        + "Viel Erfolg bei der Suche nach neuen Talenten!\nDein Probetag-Team");
    }

    private static String format(Instant instant) {
        return DATE_FORMAT.format(instant);
    }
}