package de.probetag.backend.event;

public record UserRegisteredEvent(String email, String firstName) {
}