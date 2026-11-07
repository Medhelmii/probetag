package de.probetag.backend.dto;

import de.probetag.backend.entity.Review;

import java.time.Instant;

public record CompanyReviewResponse(
        String firstName,
        String lastName,
        String email,
        String phone,
        String jobTitle,
        Instant slotDate,
        int rating,
        String comment,
        boolean interestedInJob
) {

    public static CompanyReviewResponse from(Review review) {
        var booking = review.getBooking();
        var participant = booking.getParticipant();
        return new CompanyReviewResponse(
                participant.getFirstName(),
                participant.getLastName(),
                participant.getUser().getEmail(),
                participant.getPhone(),
                booking.getSlot().getJobTitle(),
                booking.getSlot().getStartsAt(),
                review.getRating(),
                review.getComment(),
                review.isInterestedInJob());
    }
}