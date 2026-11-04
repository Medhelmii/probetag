package de.probetag.backend.service;

import de.probetag.backend.dto.AuthResponse;
import de.probetag.backend.dto.LoginRequest;
import de.probetag.backend.dto.RegisterCompanyRequest;
import de.probetag.backend.dto.RegisterParticipantRequest;

public interface AuthService {

    AuthResponse registerParticipant(RegisterParticipantRequest request);

    AuthResponse registerCompany(RegisterCompanyRequest request);

    AuthResponse login(LoginRequest request);
}