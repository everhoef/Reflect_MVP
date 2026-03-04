package direct.reflect.facilitator.facilitation.dto;

import tools.jackson.databind.ObjectMapper;
import direct.reflect.facilitator.facilitation.response.ParticipantResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for MULTI_COLUMN_BOARD component type.
 *
 * Used for both:
 * - API input (submitting categorical responses via POST)
 * - View output (displaying responses in Thymeleaf templates)
 *
 * ComponentType relationship:
 * - Produced by: MULTI_COLUMN_BOARD component
 * - Also works for single-column variants (e.g., FREEFORM is just 1 column)
 *
 * Component config constraints:
 * - columnId field maps to multiColumnConfig.columns[].id
 * - content field constrained by cardConfig.maxLength
 */
public record ColumnResponseDto(
    UUID id,

    @NotBlank(message = "Column ID is required")
    @Size(max = 50, message = "Column ID must not exceed 50 characters")
    String columnId,

    @NotBlank(message = "Content is required")
    @Size(max = 500, message = "Content must not exceed 500 characters")
    String content,

    Boolean visible,
    String participantName,
    UUID participantId,
    Integer voteCount,
    UUID clusterId,
    String clusterName
) implements ComponentResponseDto {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Convert a ParticipantResponse entity to a ColumnResponseDto.
     * Uses Jackson to automatically map HashMap keys to record fields.
     */
    public static ColumnResponseDto from(ParticipantResponse response) {
        if (response == null) {
            return null;
        }

        // Extract data fields from HashMap
        String columnId = (String) response.getResponseData().get("columnId");
        String content = (String) response.getResponseData().get("content");

        // Calculate vote count from votes list (stored as List<String> of participantIds)
        Object votesObj = response.getResponseData().get("votes");
        int voteCount = 0;
        if (votesObj instanceof java.util.List<?>) {
            voteCount = ((java.util.List<?>) votesObj).size();
        }

        return new ColumnResponseDto(
            response.getId(),
            columnId,
            content,
            response.getIsVisible(),
            response.getParticipant().getDisplayName(),
            response.getParticipant().getParticipantId(),
            voteCount,
            response.getClusterId(),
            response.getClusterName()
        );
    }

    /**
     * Convert DTO to Map for storage in ParticipantResponse.responseData.
     * Implements bidirectional mapping (opposite of from()).
     */
    @Override
    public Map<String, Object> toResponseData() {
        return Map.of(
            "columnId", columnId,
            "content", content
        );
    }
}
