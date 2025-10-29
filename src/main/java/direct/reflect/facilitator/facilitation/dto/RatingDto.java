package direct.reflect.facilitator.facilitation.dto;

import direct.reflect.facilitator.facilitation.response.ParticipantResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * DTO for RATING data pattern responses.
 * Used for both:
 * - API input (submitting ratings via POST)
 * - View output (displaying ratings in Thymeleaf templates)
 */
public record RatingDto(
    UUID id,

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 10, message = "Rating must be at most 10")
    Integer rating,

    @Size(max = 500, message = "Comment must not exceed 500 characters")
    String comment,

    Boolean visible,
    String participantName
) {
    /**
     * Convert a ParticipantResponse entity to a RatingDto.
     */
    public static RatingDto from(ParticipantResponse response) {
        if (response == null) {
            return null;
        }

        Object ratingObj = response.getResponseData().get("rating");
        Integer rating = ratingObj instanceof Number ? ((Number) ratingObj).intValue() : null;
        String comment = (String) response.getResponseData().get("comment");

        return new RatingDto(
            response.getId(),
            rating,
            comment,
            response.getIsVisible(),
            response.getParticipant().getDisplayName()
        );
    }
}
