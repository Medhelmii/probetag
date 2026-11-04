package de.probetag.backend.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record SlotRequest(
        @NotBlank @Size(max = 150) String jobTitle,
        @NotBlank @Size(max = 2000) String description,
        @NotNull @Future Instant startsAt,
        @NotNull @Future Instant endsAt,
        @Min(1) @Max(20) int capacity,
        @Size(max = 255) String dressCode
) {
}