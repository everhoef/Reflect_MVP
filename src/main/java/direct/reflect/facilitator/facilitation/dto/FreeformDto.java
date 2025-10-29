package direct.reflect.facilitator.facilitation.dto;

import direct.reflect.facilitator.facilitation.response.ParticipantResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * DTO for FREEFORM data pattern responses.
 * Used for both:
 * - API input (submitting freeform responses via POST)
 * - View output (displaying freeform responses in Thymeleaf templates)
 */
public record FreeformDto(
    UUID id,

    @NotBlank(message = "Content is required")
    @Size(max = 1000, message = "Content must not exceed 1000 characters")
    String content,

    @Size(max = 200, message = "Tags must not exceed 200 characters")
    String tags,

    Boolean isMultiLine,
    Boolean isVisible,
    String participantName
) {
    /**
     * Convert a ParticipantResponse entity to a FreeformDto.
     */
    public static FreeformDto from(ParticipantResponse response) {
        if (response == null) {
            return null;
        }

        String content = (String) response.getResponseData().get("content");
        String tags = (String) response.getResponseData().get("tags");
        Object isMultiLineObj = response.getResponseData().get("isMultiLine");
        Boolean isMultiLine = isMultiLineObj instanceof Boolean ? (Boolean) isMultiLineObj : null;

        return new FreeformDto(
            response.getId(),
            content,
            tags,
            isMultiLine,
            response.getIsVisible(),
            response.getParticipant().getDisplayName()
        );
    }
}
