package de.probetag.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterCompanyRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 100) String sector,
        @NotBlank @Size(max = 255) String address,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Pattern(regexp = "\\d{5}", message = "muss eine 5-stellige Postleitzahl sein") String postalCode,
        @Size(max = 2000) String description
) {
}