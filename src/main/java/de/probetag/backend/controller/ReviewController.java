package de.probetag.backend.controller;

import de.probetag.backend.dto.CompanyRatingResponse;
import de.probetag.backend.dto.CompanyReviewResponse;
import de.probetag.backend.dto.PublicReviewResponse;
import de.probetag.backend.dto.ReviewRequest;
import de.probetag.backend.security.AuthenticatedUser;
import de.probetag.backend.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/bookings/{bookingId}/review")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PARTICIPANT')")
    public PublicReviewResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                       @PathVariable UUID bookingId,
                                       @Valid @RequestBody ReviewRequest request) {
        return reviewService.create(user.id(), bookingId, request);
    }

    @GetMapping("/companies/{companyId}/reviews")
    public CompanyRatingResponse publicReviews(@PathVariable UUID companyId) {
        return reviewService.findPublicByCompany(companyId);
    }

    @GetMapping("/companies/me/reviews")
    @PreAuthorize("hasRole('COMPANY')")
    public List<CompanyReviewResponse> myReviews(@AuthenticationPrincipal AuthenticatedUser user) {
        return reviewService.findForMyCompany(user.id());
    }
}