package direct.reflect.facilitator.facilitation.dto;

import java.util.UUID;

import java.util.Map;

/**
 * Sealed interface for all component response DTOs.
 *
 * This interface establishes a type-safe hierarchy for response data from different ComponentTypes.
 * Using a sealed interface enables:
 * - Exhaustive pattern matching (Java 17+)
 * - Compile-time verification of all response types
 * - Common contract for all component responses
 * - Bidirectional mapping (Map → DTO via from(), DTO → Map via toResponseData())
 *
 * Architecture:
 * ComponentType → ResponseDto → ComponentConfig
 * ───────────────────────────────────────────────
 * MULTI_COLUMN_BOARD → ColumnResponseDto → multiColumnConfig.columns[].id
 * RATING_SCALE → RatingResponseDto → ratingScaleConfig.min/max
 * HISTOGRAM_CHART → (displays RatingResponseDto) → N/A
 */
public sealed interface ComponentResponseDto
    permits ColumnResponseDto, RatingResponseDto {

    /**
     * Unique identifier for this response
     */
    UUID id();

    /**
     * Whether this response is visible to all participants.
     * False = hidden until facilitator reveals
     * True = visible to everyone
     */
    Boolean visible();

    /**
     * Display name of the participant who submitted this response
     */
    String participantName();

    /**
     * Unique identifier of the participant who submitted this response.
     * Used for self-view filtering (e.g., "edit your own cards")
     */
    UUID participantId();

    /**
     * Convert DTO to Map for storage in ParticipantResponse.responseData.
     * Each implementing DTO defines its own field mapping.
     *
     * @return Map containing the response data fields
     */
    Map<String, Object> toResponseData();
}
