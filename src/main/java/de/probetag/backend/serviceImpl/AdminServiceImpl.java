package de.probetag.backend.serviceImpl;

import de.probetag.backend.dto.AdminCompanyResponse;
import de.probetag.backend.entity.Company;
import de.probetag.backend.entity.Role;
import de.probetag.backend.entity.User;
import de.probetag.backend.event.CompanyVerifiedEvent;
import de.probetag.backend.exception.ConflictException;
import de.probetag.backend.exception.ForbiddenException;
import de.probetag.backend.exception.ResourceNotFoundException;
import de.probetag.backend.repository.CompanyRepository;
import de.probetag.backend.repository.UserRepository;
import de.probetag.backend.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public List<AdminCompanyResponse> findCompanies(boolean verified) {
        return companyRepository.findByVerifiedOrderByNameAsc(verified)
                .stream()
                .map(AdminCompanyResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public AdminCompanyResponse verifyCompany(UUID companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Unternehmen nicht gefunden"));

        if (company.isVerified()) {
            throw new ConflictException("Dieses Unternehmen ist bereits freigeschaltet");
        }

        company.setVerified(true);
        eventPublisher.publishEvent(new CompanyVerifiedEvent(company.getUser().getEmail(), company.getName()));

        return AdminCompanyResponse.from(company);
    }

    @Override
    @Transactional
    public void setUserEnabled(UUID userId, boolean enabled) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Benutzer nicht gefunden"));

        if (user.getRole() == Role.ADMIN) {
            throw new ForbiddenException("Admin-Konten können nicht gesperrt werden");
        }

        user.setEnabled(enabled);
    }
}