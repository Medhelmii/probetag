package de.probetag.backend.serviceImpl;

import de.probetag.backend.dto.AuthResponse;
import de.probetag.backend.dto.LoginRequest;
import de.probetag.backend.dto.RegisterCompanyRequest;
import de.probetag.backend.dto.RegisterParticipantRequest;
import de.probetag.backend.entity.Company;
import de.probetag.backend.entity.Participant;
import de.probetag.backend.entity.Role;
import de.probetag.backend.entity.User;
import de.probetag.backend.exception.EmailAlreadyUsedException;
import de.probetag.backend.exception.InvalidCredentialsException;
import de.probetag.backend.repository.CompanyRepository;
import de.probetag.backend.repository.ParticipantRepository;
import de.probetag.backend.repository.UserRepository;
import de.probetag.backend.security.JwtService;
import de.probetag.backend.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final ParticipantRepository participantRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Override
    @Transactional
    public AuthResponse registerParticipant(RegisterParticipantRequest request) {
        User user = createUser(request.email(), request.password(), Role.PARTICIPANT);

        Participant participant = new Participant();
        participant.setUser(user);
        participant.setFirstName(request.firstName().trim());
        participant.setLastName(request.lastName().trim());
        participant.setPhone(request.phone());
        participant.setMotivation(request.motivation());
        participantRepository.save(participant);

        return toResponse(user);
    }

    @Override
    @Transactional
    public AuthResponse registerCompany(RegisterCompanyRequest request) {
        User user = createUser(request.email(), request.password(), Role.COMPANY);

        Company company = new Company();
        company.setUser(user);
        company.setName(request.name().trim());
        company.setSector(request.sector().trim());
        company.setAddress(request.address().trim());
        company.setCity(request.city().trim());
        company.setPostalCode(request.postalCode());
        company.setDescription(request.description());
        companyRepository.save(company);

        return toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalize(request.email()))
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .filter(User::isEnabled)
                .orElseThrow(InvalidCredentialsException::new);

        return toResponse(user);
    }

    private User createUser(String email, String rawPassword, Role role) {
        String normalizedEmail = normalize(email);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyUsedException(normalizedEmail);
        }
        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        return userRepository.save(user);
    }

    private AuthResponse toResponse(User user) {
        return new AuthResponse(
                jwtService.generateToken(user),
                "Bearer",
                jwtService.getExpirationSeconds(),
                user.getId(),
                user.getEmail(),
                user.getRole());
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}