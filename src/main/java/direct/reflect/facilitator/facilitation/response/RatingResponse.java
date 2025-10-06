package direct.reflect.facilitator.facilitation.response;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import direct.reflect.facilitator.configurator.DataPattern;

/**
 * Rating response for scale-based activities like:
 * - Happiness Histogram (1-10)
 * - ROTI - Return on Time Invested (1-5)
 * - Team Satisfaction (1-5)
 * - Confidence Level (1-10)
 *
 * Supports:
 * - Numeric ratings with configurable min/max
 * - Optional comment explaining the rating
 * - Histogram visualization (aggregated by service layer)
 */
@Entity
@DiscriminatorValue("RATING")
@Data
@EqualsAndHashCode(callSuper = true)
public class RatingResponse extends ParticipantResponse {

    @Column(nullable = false)
    private Integer rating; // Numeric rating value

    @Column
    private Integer minRating; // For validation and histogram display (e.g., 1)

    @Column
    private Integer maxRating; // For validation and histogram display (e.g., 10)

    @Column(columnDefinition = "TEXT")
    private String comment; // Optional explanation of rating

    @Override
    public DataPattern getDataPattern() {
        return DataPattern.RATING;
    }

    @Override
    public String getDisplaySummary() {
        String summary = String.format("Rating: %d", rating);
        if (comment != null && !comment.trim().isEmpty()) {
            summary += " - " + (comment.length() > 30 ? comment.substring(0, 27) + "..." : comment);
        }
        return summary;
    }
}
