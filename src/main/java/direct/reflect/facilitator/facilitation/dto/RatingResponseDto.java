package direct.reflect.facilitator.facilitation.dto;

import direct.reflect.facilitator.facilitation.response.ParticipantResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for RATING_SCALE component type.
 *
 * Used for both:
 * - API input (submitting ratings via POST)
 * - View output (displaying ratings in Thymeleaf templates)
 *
 * ComponentType relationship:
 * - Produced by: RATING_SCALE component
 * - Displayed by: HISTOGRAM_CHART component (shows aggregated view)
 *
 * Component config constraints:
 * - rating field constrained by ratingScaleConfig.min/max
 * - comment field optional, constrained by maxLength if present
 */
public record RatingResponseDto(
    UUID id,

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 10, message = "Rating must be at most 10")
    Integer rating,

    @Size(max = 500, message = "Comment must not exceed 500 characters")
    String comment,

    Boolean visible,
    String participantName,
    UUID participantId
) implements ComponentResponseDto {


    /**
     * Convert a ParticipantResponse entity to a RatingResponseDto.
     * Uses Jackson to automatically map HashMap keys to record fields.
     */
    public static RatingResponseDto from(ParticipantResponse response) {
        if (response == null) {
            return null;
        }

        // Extract data fields from HashMap
        Object ratingObj = response.getResponseData().get("rating");
        Integer rating = ratingObj instanceof Number ? ((Number) ratingObj).intValue() : null;
        String comment = (String) response.getResponseData().get("comment");

        return new RatingResponseDto(
            response.getId(),
            rating,
            comment,
            response.getIsVisible(),
            response.getParticipant().getDisplayName(),
            response.getParticipant().getParticipantId()
        );
    }

    /**
     * Convert DTO to Map for storage in ParticipantResponse.responseData.
     * Implements bidirectional mapping (opposite of from()).
     * Only includes comment if it's not null or blank.
     */
    @Override
    public Map<String, Object> toResponseData() {
        Map<String, Object> data = new HashMap<>();
        data.put("rating", rating);
        if (comment != null && !comment.isBlank()) {
            data.put("comment", comment);
        }
        return data;
    }
}
