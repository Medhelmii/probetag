package de.probetag.backend.serviceImpl;

import de.probetag.backend.dto.CompanyRatingResponse;
import de.probetag.backend.dto.CompanyReviewResponse;
import de.probetag.backend.dto.PublicReviewResponse;
import de.probetag.backend.dto.ReviewRequest;
import de.probetag.backend.entity.Booking;
import de.probetag.backend.entity.BookingStatus;
import de.probetag.backend.entity.Company;
import de.probetag.backend.entity.Participant;
import de.probetag.backend.entity.Review;
import de.probetag.backend.exception.ConflictException;
import de.probetag.backend.exception.ForbiddenException;
import de.probetag.backend.exception.ResourceNotFoundException;
import de.probetag.backend.repository.BookingRepository;
import de.probetag.backend.repository.CompanyRepository;
import de.probetag.backend.repository.ParticipantRepository;
import de.probetag.backend.repository.ReviewRepository;
import de.probetag.backend.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;
    private final ParticipantRepository participantRepository;
    private final CompanyRepository companyRepository;

    @Override
    @Transactional
    public PublicReviewResponse create(UUID userId, UUID bookingId, ReviewRequest request) {
        Participant participant = participantRepository.findByUserId(userId)
                .orElseThrow(() -> new ForbiddenException("Nur Teilnehmer können bewerten"));
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Buchung nicht gefunden"));

        if (!booking.getParticipant().getId().equals(participant.getId())) {
            throw new ForbiddenException("Diese Buchung gehört nicht dir");
        }
        if (booking.getStatus() != BookingStatus.ATTENDED) {
            throw new ConflictException("Bewerten ist erst nach der Teilnahme am Probetag möglich");
        }
        if (reviewRepository.existsByBookingId(bookingId)) {
            throw new ConflictException("Du hast diesen Probetag bereits bewertet");
        }

        Review review = new Review();
        review.setBooking(booking);
        review.setRating(request.rating());
        review.setComment(request.comment());
        review.setInterestedInJob(request.interestedInJob());

        return PublicReviewResponse.from(reviewRepository.save(review));
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyRatingResponse findPublicByCompany(UUID companyId) {
        if (!companyRepository.existsById(companyId)) {
            throw new ResourceNotFoundException("Unternehmen nicht gefunden");
        }

        List<PublicReviewResponse> reviews = reviewRepository.findByCompanyId(companyId)
                .stream()
                .map(PublicReviewResponse::from)
                .toList();

        Double average = reviewRepository.averageRatingOfCompany(companyId);
        Double rounded = (average == null) ? null : Math.round(average * 10) / 10.0;

        return new CompanyRatingResponse(rounded, reviews.size(), reviews);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompanyReviewResponse> findForMyCompany(UUID userId) {
        Company company = companyRepository.findByUserId(userId)
                .orElseThrow(() -> new ForbiddenException("Nur Unternehmen haben Zugriff"));

        return reviewRepository.findByCompanyId(company.getId())
                .stream()
                .map(CompanyReviewResponse::from)
                .toList();
    }
}