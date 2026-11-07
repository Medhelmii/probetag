package de.probetag.backend.dto;

import jakarta.validation.constraints.NotNull;

public record AttendanceRequest(
        @NotNull Boolean attended
) {
}