package de.probetag.backend.exception;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("E-Mail oder Passwort ist falsch");
    }
}