package de.probetag.backend.event;

public record CompanyVerifiedEvent(String email, String companyName) {
}