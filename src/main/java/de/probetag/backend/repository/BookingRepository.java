package de.probetag.backend.repository;

import de.probetag.backend.entity.Booking;
import de.probetag.backend.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    long countBySlotIdAndStatus(UUID slotId, BookingStatus status);

    Optional<Booking> findBySlotIdAndParticipantId(UUID slotId, UUID participantId);

    List<Booking> findByParticipantIdOrderBySlotStartsAtDesc(UUID participantId);

    List<Booking> findBySlotIdOrderByCreatedAtAsc(UUID slotId);
}