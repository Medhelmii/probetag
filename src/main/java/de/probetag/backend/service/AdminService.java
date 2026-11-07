package de.probetag.backend.service;

import de.probetag.backend.dto.AdminCompanyResponse;

import java.util.List;
import java.util.UUID;

public interface AdminService {

    List<AdminCompanyResponse> findCompanies(boolean verified);

    AdminCompanyResponse verifyCompany(UUID companyId);

    void setUserEnabled(UUID userId, boolean enabled);
}