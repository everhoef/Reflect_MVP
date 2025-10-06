package direct.reflect.facilitator.facilitation.response;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import direct.reflect.facilitator.configurator.DataPattern;

/**
 * Categorical response (sticky notes) for activities like:
 * - Mad, Sad, Glad
 * - Start, Stop, Continue
 * - Worked Well, Do Differently
 * - ESVP (Explorer, Shopper, Vacationer, Prisoner)
 *
 * Supports:
 * - Categorization (category field)
 * - Clustering (via ResponseCluster relationship)
 * - Voting (via ResponseVote)
 * - Color coding (optional)
 */
@Entity
@DiscriminatorValue("CATEGORICAL")
@Data
@EqualsAndHashCode(callSuper = true)
public class CategoricalResponse extends ParticipantResponse {

    @Column(nullable = false, length = 100)
    private String category; // e.g., "Mad", "Sad", "Glad"

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content; // The actual sticky note text

    @Column(length = 20)
    private String color; // Optional: hex color for visual coding (#FF5733)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cluster_id")
    private ResponseCluster cluster; // For grouping similar sticky notes

    @Override
    public DataPattern getDataPattern() {
        return DataPattern.CATEGORICAL;
    }

    @Override
    public String getDisplaySummary() {
        return String.format("[%s] %s", category,
            content.length() > 50 ? content.substring(0, 47) + "..." : content);
    }
}
