package direct.reflect.facilitator.facilitation.response;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import direct.reflect.facilitator.configurator.DataPattern;

/**
 * Freeform text response for open-ended activities like:
 * - One Word check-in
 * - Kudos cards
 * - Closing statements
 * - Open questions
 * - Feedback comments
 *
 * Supports:
 * - Free text content (no structure)
 * - Optional tagging for categorization
 * - Word cloud generation (aggregated by service layer)
 */
@Entity
@DiscriminatorValue("FREEFORM")
@Data
@EqualsAndHashCode(callSuper = true)
public class FreeformResponse extends ParticipantResponse {

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content; // The actual text response

    @Column
    private String tags; // Comma-separated tags for optional categorization

    @Column(nullable = false)
    private Boolean isMultiLine = false; // True for longer responses, false for one-word

    @Override
    public DataPattern getDataPattern() {
        return DataPattern.FREEFORM;
    }

    @Override
    public String getDisplaySummary() {
        if (content.length() > 100) {
            return content.substring(0, 97) + "...";
        }
        return content;
    }
}
