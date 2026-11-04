package de.probetag.backend.repository;

import de.probetag.backend.entity.Participant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ParticipantRepository extends JpaRepository<Participant, UUID> {
}