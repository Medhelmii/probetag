package de.probetag.backend.repository;

import de.probetag.backend.entity.Booking;
import de.probetag.backend.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    long countBySlotIdAndStatus(UUID slotId, BookingStatus status);

    Optional<Booking> findBySlotIdAndParticipantId(UUID slotId, UUID participantId);

    List<Booking> findByParticipantIdOrderBySlotStartsAtDesc(UUID participantId);

    List<Booking> findBySlotIdOrderByCreatedAtAsc(UUID slotId);

    @Query("""
            select b from Booking b
            join fetch b.slot s
            join fetch s.company c
            join fetch b.participant p
            join fetch p.user u
            where b.status = :status
              and b.reminderSentAt is null
              and s.startsAt >= :from
              and s.startsAt < :to
            """)
    List<Booking> findForReminder(@Param("status") BookingStatus status,
                                  @Param("from") Instant from,
                                  @Param("to") Instant to);


    @Query("""
            select count(b) from Booking b
            where b.participant.id = :participantId
              and b.status = :status
              and b.slot.startsAt > :since
            """)
    long countByParticipantAndStatusSince(@Param("participantId") UUID participantId,
                                          @Param("status") BookingStatus status,
                                          @Param("since") Instant since);
}