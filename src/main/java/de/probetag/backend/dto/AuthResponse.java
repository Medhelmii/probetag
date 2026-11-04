package de.probetag.backend.dto;

import de.probetag.backend.entity.Role;

import java.util.UUID;

public record AuthResponse(
        String token,
        String tokenType,
        long expiresInSeconds,
        UUID userId,
        String email,
        Role role
) {
}