package de.probetag.backend.controller;

import de.probetag.backend.dto.AuthResponse;
import de.probetag.backend.dto.LoginRequest;
import de.probetag.backend.dto.RegisterCompanyRequest;
import de.probetag.backend.dto.RegisterParticipantRequest;
import de.probetag.backend.service.AuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "Registrierung und Login")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register/participant")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse registerParticipant(@Valid @RequestBody RegisterParticipantRequest request) {
        return authService.registerParticipant(request);
    }

    @PostMapping("/register/company")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse registerCompany(@Valid @RequestBody RegisterCompanyRequest request) {
        return authService.registerCompany(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}