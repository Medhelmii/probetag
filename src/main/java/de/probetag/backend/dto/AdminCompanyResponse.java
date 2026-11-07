package de.probetag.backend.dto;

import de.probetag.backend.entity.Company;

import java.util.UUID;

public record AdminCompanyResponse(
        UUID id,
        UUID userId,
        String name,
        String sector,
        String address,
        String city,
        String email,
        boolean verified,
        boolean enabled
) {

    public static AdminCompanyResponse from(Company company) {
        var user = company.getUser();
        return new AdminCompanyResponse(
                company.getId(),
                user.getId(),
                company.getName(),
                company.getSector(),
                company.getAddress(),
                company.getCity(),
                user.getEmail(),
                company.isVerified(),
                user.isEnabled());
    }
}