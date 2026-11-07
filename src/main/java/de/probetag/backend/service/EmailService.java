package de.probetag.backend.service;

public interface EmailService {

    boolean send(String to, String subject, String text);}