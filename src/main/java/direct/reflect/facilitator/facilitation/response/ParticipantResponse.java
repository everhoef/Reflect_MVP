package direct.reflect.facilitator.facilitation.response;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import direct.reflect.facilitator.facilitation.Participant;
import direct.reflect.facilitator.configurator.RetroStep;
import direct.reflect.facilitator.configurator.DataPattern;
import direct.reflect.facilitator.common.entity.GeneratedUuidV7;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Base class for all participant responses using JSON column storage.
 *
 * Design rationale:
 * - PostgreSQL JSONB column for type-specific fields (flexible, indexable)
 * - Single table for easier data analysis and cross-pattern queries (POC requirement)
 * - DataPattern enum identifies response type
 * - No NULL constraint conflicts - each response has its own JSON structure
 * - Easy schema evolution - add fields without migrations
 *
 * Response patterns stored in responseData JSON:
 * - CATEGORICAL: {category, content, color}
 * - RATING: {rating, minRating, maxRating, comment}
 * - FREEFORM: {content, tags, isMultiLine}
 */
@Entity
@Table(name = "participant_responses")
@Data
public class ParticipantResponse {

    @Id
    @GeneratedUuidV7
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "retro_step_id", nullable = false)
    private RetroStep retroStep;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(name = "participant_id", referencedColumnName = "participant_id", nullable = false),
        @JoinColumn(name = "session_id", referencedColumnName = "session_id", nullable = false)
    })
    private Participant participant;

    @Column(nullable = false)
    private LocalDateTime submittedAt = LocalDateTime.now();

    @Column
    private LocalDateTime editedAt;

    @Column(nullable = false)
    private Boolean isVisible = false; // PRIVATE by default, facilitator reveals

    @Column(nullable = false)
    private Integer displayOrder = 0; // Order within category/cluster

    /**
     * Response type pattern (CATEGORICAL, RATING, FREEFORM)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DataPattern dataPattern;

    /**
     * Type-specific response data stored as JSON.
     * Structure depends on dataPattern:
     * - CATEGORICAL: {category: "Mad", content: "text", color: "#FF5733"}
     * - RATING: {rating: 8, minRating: 1, maxRating: 10, comment: "text"}
     * - FREEFORM: {content: "text", tags: "tag1,tag2", isMultiLine: false}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> responseData = new HashMap<>();

    /**
     * Get a display-friendly summary of this response.
     */
    @Transient
    public String getDisplaySummary() {
        return switch (dataPattern) {
            case RATING -> {
                Integer rating = (Integer) responseData.get("rating");
                String comment = (String) responseData.get("comment");
                String summary = "Rating: " + rating;
                if (comment != null && !comment.trim().isEmpty()) {
                    summary += " - " + (comment.length() > 30 ? comment.substring(0, 27) + "..." : comment);
                }
                yield summary;
            }
            case CATEGORICAL -> {
                String category = (String) responseData.get("category");
                String content = (String) responseData.get("content");
                yield String.format("[%s] %s", category,
                    content.length() > 50 ? content.substring(0, 47) + "..." : content);
            }
            case FREEFORM -> {
                String content = (String) responseData.get("content");
                if (content.length() > 100) {
                    yield content.substring(0, 97) + "...";
                }
                yield content;
            }
        };
    }
}
