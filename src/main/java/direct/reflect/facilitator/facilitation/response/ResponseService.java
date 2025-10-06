package direct.reflect.facilitator.facilitation.response;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import direct.reflect.facilitator.facilitation.RetroSession;
import direct.reflect.facilitator.facilitation.Participant;
import direct.reflect.facilitator.configurator.RetroStep;
import direct.reflect.facilitator.configurator.RetroStepRepository;
import direct.reflect.facilitator.eventing.EventService;
import direct.reflect.facilitator.eventing.RetroEvent;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Unified service for managing all participant responses across the 3 patterns.
 *
 * Handles:
 * - CATEGORICAL: Sticky notes with categories (Mad/Sad/Glad, Start/Stop/Continue)
 * - RATING: Numeric ratings with comments (Happiness Histogram, ROTI)
 * - FREEFORM: Open text responses (One Word, Kudos, Closing Statements)
 *
 * Future enhancements (post-POC):
 * - Clustering support for CATEGORICAL
 * - Voting support across all patterns
 * - Word cloud generation for FREEFORM
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ResponseService {

    private final ParticipantResponseRepository responseRepository;
    private final RetroStepRepository retroStepRepository;
    private final EventService eventService;

    // ========== CATEGORICAL RESPONSES ==========

    /**
     * Submit a categorical response (sticky note).
     * Validates inputs and handles event publishing.
     *
     * @param session The retro session
     * @param stepId The step ID
     * @param participant The participant submitting the response
     * @param category The category for this sticky note (e.g., "Mad", "Sad", "Glad")
     * @param content The text content of the sticky note
     */
    public CategoricalResponse submitCategoricalResponse(
            RetroSession session,
            Long stepId,
            Participant participant,
            String category,
            String content) {

        // Validation
        if (category == null || category.trim().isEmpty()) {
            throw new IllegalArgumentException("Category cannot be empty");
        }
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Content cannot be empty");
        }

        RetroStep step = getRetroStepById(stepId);

        CategoricalResponse response = new CategoricalResponse();
        response.setRetroStep(step);
        response.setParticipant(participant);
        response.setCategory(category.trim());
        response.setContent(content.trim());
        response.setIsVisible(false); // Private by default

        CategoricalResponse savedResponse = responseRepository.save(response);
        log.info("Submitted categorical response for participant {} in step {} (category: {})",
            participant.getDisplayName(), stepId, category);

        // Publish event
        publishResponseSubmittedEvent(session.getId(), savedResponse);

        return savedResponse;
    }

    // ========== RATING RESPONSES ==========

    /**
     * Submit or update a rating response.
     * Participants can update their rating (only one rating per participant per step).
     * Validates rating is within min/max bounds.
     *
     * @param session The retro session
     * @param stepId The step ID
     * @param participant The participant submitting the response
     * @param rating The numeric rating value
     * @param minRating Minimum allowed rating (for validation)
     * @param maxRating Maximum allowed rating (for validation)
     * @param comment Optional comment explaining the rating
     */
    public RatingResponse submitRatingResponse(
            RetroSession session,
            Long stepId,
            Participant participant,
            Integer rating,
            Integer minRating,
            Integer maxRating,
            String comment) {

        // Validation
        if (rating == null) {
            throw new IllegalArgumentException("Rating cannot be null");
        }
        if (rating < minRating || rating > maxRating) {
            throw new IllegalArgumentException(
                String.format("Rating must be between %d and %d", minRating, maxRating));
        }

        RetroStep step = getRetroStepById(stepId);

        // For RATING pattern, participants can update their existing response
        Optional<ParticipantResponse> existing = responseRepository
            .findBySessionAndRetroStepAndParticipant(session, step, participant);

        RatingResponse response;
        if (existing.isPresent() && existing.get() instanceof RatingResponse) {
            response = (RatingResponse) existing.get();
            response.setRating(rating);
            response.setComment(comment != null ? comment.trim() : null);
            response.setMinRating(minRating);
            response.setMaxRating(maxRating);
            response.setEditedAt(java.time.LocalDateTime.now());
            log.info("Updated rating response for participant {} in step {}", participant.getDisplayName(), stepId);
        } else {
            response = new RatingResponse();
            response.setRetroStep(step);
            response.setParticipant(participant);
            response.setRating(rating);
            response.setMinRating(minRating);
            response.setMaxRating(maxRating);
            response.setComment(comment != null ? comment.trim() : null);
            response.setIsVisible(false);
            log.info("Submitted new rating response for participant {} in step {}", participant.getDisplayName(), stepId);
        }

        RatingResponse savedResponse = responseRepository.save(response);

        // Publish event
        publishResponseSubmittedEvent(session.getId(), savedResponse);

        return savedResponse;
    }

    // ========== FREEFORM RESPONSES ==========

    /**
     * Submit a freeform text response.
     * Validates content is not empty.
     *
     * @param session The retro session
     * @param stepId The step ID
     * @param participant The participant submitting the response
     * @param content The text content
     * @param isMultiLine Whether this is a multi-line response (vs one-word)
     */
    public FreeformResponse submitFreeformResponse(
            RetroSession session,
            Long stepId,
            Participant participant,
            String content,
            Boolean isMultiLine) {

        // Validation
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Content cannot be empty");
        }

        RetroStep step = getRetroStepById(stepId);

        FreeformResponse response = new FreeformResponse();
        response.setRetroStep(step);
        response.setParticipant(participant);
        response.setContent(content.trim());
        response.setIsMultiLine(isMultiLine != null ? isMultiLine : false);
        response.setIsVisible(false);

        FreeformResponse savedResponse = responseRepository.save(response);
        log.info("Submitted freeform response for participant {} in step {}",
            participant.getDisplayName(), stepId);

        // Publish event
        publishResponseSubmittedEvent(session.getId(), savedResponse);

        return savedResponse;
    }

    // ========== QUERY METHODS ==========

    public List<ParticipantResponse> getResponsesForStep(RetroSession session, RetroStep step) {
        return responseRepository.findBySessionAndRetroStep(session, step);
    }

    public List<ParticipantResponse> getVisibleResponsesForStep(RetroSession session, RetroStep step) {
        return responseRepository.findVisibleBySessionAndRetroStep(session, step);
    }

    public Long getParticipationCount(RetroSession session, RetroStep step) {
        return responseRepository.countResponsesForStep(session, step);
    }

    // ========== VISIBILITY CONTROL ==========

    /**
     * Reveal all responses for a step (PRIVATE → PUBLIC transition).
     * Used after participants submit responses privately.
     * Only facilitators can call this (validation done in controller).
     *
     * @param session The retro session
     * @param stepId The step ID
     */
    public void revealAllResponses(RetroSession session, Long stepId) {
        RetroStep step = getRetroStepById(stepId);

        List<ParticipantResponse> responses = responseRepository.findBySessionAndRetroStep(session, step);
        responses.forEach(response -> response.setIsVisible(true));
        responseRepository.saveAll(responses);

        log.info("Revealed {} responses for step {} in session {}", responses.size(), stepId, session.getId());

        // Publish event
        try {
            eventService.publish(RetroEvent.responsesRevealed(session.getId(), "facilitator", stepId));
        } catch (Exception eventError) {
            log.error("Failed to publish responses revealed event: {}", eventError.getMessage());
        }
    }

    // ========== HELPER METHODS ==========

    private RetroStep getRetroStepById(Long stepId) {
        return retroStepRepository.findById(stepId)
            .orElseThrow(() -> new IllegalArgumentException("RetroStep not found with ID: " + stepId));
    }

    private void publishResponseSubmittedEvent(java.util.UUID retroId, ParticipantResponse response) {
        try {
            RetroEvent.ResponseData responseData = new RetroEvent.ResponseData(
                response.getId(),
                response.getRetroStep().getId(),
                response.getParticipant().getParticipantId().toString(),
                response.getParticipant().getDisplayName(),
                response.getDisplaySummary(), // Use polymorphic method
                null, // category (handled by CategoricalResponse if needed)
                null, // rating (handled by RatingResponse if needed)
                null, // comment (handled by RatingResponse if needed)
                response.getIsVisible(),
                response.getSubmittedAt().atZone(java.time.ZoneId.systemDefault()).toInstant()
            );
            eventService.publish(RetroEvent.responseSubmitted(retroId, response.getParticipant().getParticipantId().toString(), responseData));
        } catch (Exception eventError) {
            log.error("Failed to publish response submitted event: {}", eventError.getMessage());
        }
    }
}
