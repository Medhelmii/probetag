package de.probetag.backend.repository;

import de.probetag.backend.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    Optional<Company> findByUserId(UUID userId);
    List<Company> findByVerifiedOrderByNameAsc(boolean verified);
}