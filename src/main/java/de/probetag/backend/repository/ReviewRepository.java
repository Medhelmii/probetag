package de.probetag.backend.repository;

import de.probetag.backend.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    boolean existsByBookingId(UUID bookingId);

    @Query("""
            select r from Review r
            join fetch r.booking b
            join fetch b.slot s
            join fetch b.participant p
            join fetch p.user u
            where s.company.id = :companyId
            order by s.startsAt desc
            """)
    List<Review> findByCompanyId(@Param("companyId") UUID companyId);

    @Query("select avg(r.rating) from Review r where r.booking.slot.company.id = :companyId")
    Double averageRatingOfCompany(@Param("companyId") UUID companyId);
}