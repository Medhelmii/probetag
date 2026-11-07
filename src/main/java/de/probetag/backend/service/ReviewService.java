package de.probetag.backend.service;

import de.probetag.backend.dto.CompanyRatingResponse;
import de.probetag.backend.dto.CompanyReviewResponse;
import de.probetag.backend.dto.PublicReviewResponse;
import de.probetag.backend.dto.ReviewRequest;

import java.util.List;
import java.util.UUID;

public interface ReviewService {

    PublicReviewResponse create(UUID userId, UUID bookingId, ReviewRequest request);

    CompanyRatingResponse findPublicByCompany(UUID companyId);

    List<CompanyReviewResponse> findForMyCompany(UUID userId);
}