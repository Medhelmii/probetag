package de.probetag.backend.repository;

import de.probetag.backend.entity.Slot;
import de.probetag.backend.entity.SlotStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SlotRepository extends JpaRepository<Slot, UUID> {

    List<Slot> findByCompanyIdOrderByStartsAtAsc(UUID companyId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Slot s where s.id = :id")
    Optional<Slot> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select s from Slot s
            join fetch s.company c
            where s.status = :status
              and s.startsAt > :from
              and (:city is null or lower(c.city) = :city)
              and (:sector is null or lower(c.sector) = :sector)
            order by s.startsAt
            """)
    List<Slot> search(@Param("status") SlotStatus status,
                      @Param("from") Instant from,
                      @Param("city") String city,
                      @Param("sector") String sector);
}