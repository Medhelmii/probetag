package de.probetag.backend.dto;

import de.probetag.backend.entity.Review;

public record PublicReviewResponse(
        String firstName,
        String jobTitle,
        int rating,
        String comment
) {

    public static PublicReviewResponse from(Review review) {
        var booking = review.getBooking();
        return new PublicReviewResponse(
                booking.getParticipant().getFirstName(),
                booking.getSlot().getJobTitle(),
                review.getRating(),
                review.getComment());
    }
}