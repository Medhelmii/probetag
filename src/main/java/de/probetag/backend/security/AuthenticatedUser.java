package de.probetag.backend.security;

import de.probetag.backend.entity.Role;

import java.util.UUID;

public record AuthenticatedUser(UUID id, String email, Role role) {
}