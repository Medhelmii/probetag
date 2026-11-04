package de.probetag.backend.exception;

public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException(String email) {
        super("Die E-Mail-Adresse " + email + " ist bereits registriert");
    }
}