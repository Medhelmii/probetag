package de.probetag.backend.dto;

import java.util.List;

public record CompanyRatingResponse(
        Double averageRating,
        int reviewCount,
        List<PublicReviewResponse> reviews
) {
}