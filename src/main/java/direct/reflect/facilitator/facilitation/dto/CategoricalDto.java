package direct.reflect.facilitator.facilitation.dto;

import direct.reflect.facilitator.facilitation.response.ParticipantResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * DTO for CATEGORICAL data pattern responses.
 * Used for both:
 * - API input (submitting categorical responses via POST)
 * - View output (displaying categorical responses in Thymeleaf templates)
 */
public record CategoricalDto(
    UUID id,

    @NotBlank(message = "Category is required")
    @Size(max = 50, message = "Category must not exceed 50 characters")
    String category,

    @NotBlank(message = "Content is required")
    @Size(max = 500, message = "Content must not exceed 500 characters")
    String content,

    @Size(max = 7, message = "Color must be a valid hex color")
    String color,

    Boolean isVisible,
    String participantName
) {
    /**
     * Convert a ParticipantResponse entity to a CategoricalDto.
     */
    public static CategoricalDto from(ParticipantResponse response) {
        if (response == null) {
            return null;
        }

        String category = (String) response.getResponseData().get("category");
        String content = (String) response.getResponseData().get("content");
        String color = (String) response.getResponseData().get("color");

        return new CategoricalDto(
            response.getId(),
            category,
            content,
            color,
            response.getIsVisible(),
            response.getParticipant().getDisplayName()
        );
    }
}
