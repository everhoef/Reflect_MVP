package direct.reflect.facilitator.facilitation.response;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import direct.reflect.facilitator.facilitation.Participant;
import direct.reflect.facilitator.configurator.RetroStep;
import direct.reflect.facilitator.common.entity.GeneratedUuidV7;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Simplified ParticipantResponse entity using pure JSON storage.
 *
 * Design rationale (Wizard Pattern):
 * - PostgreSQL JSONB column for all response data (flexible, indexable)
 * - Response structure determined by RetroStep's componentType (no need for separate enum)
 * - Single table for easier data analysis and cross-pattern queries
 * - Visibility control for privacy (PRIVATE by default, facilitator reveals)
 * - Easy schema evolution - add fields without migrations
 *
 * Response data examples by component type:
 * - MULTI_COLUMN_BOARD: {category: "Mad", content: "text"}
 * - RATING_SCALE: {rating: 8, comment: "text"}
 * - Other components can define their own structure
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

    /**
     * Visibility control for privacy.
     * PRIVATE (false) by default - facilitator can reveal all responses with single click.
     */
    @Column(nullable = false)
    private Boolean isVisible = false;

    /**
     * Display order within category/cluster (for sorting, drag-drop positioning).
     */
    @Column(nullable = false)
    private Integer displayOrder = 0;

    /**
     * Cluster identifier for grouping related responses together.
     * Null means the response has not been assigned to any cluster yet.
     */
    @Column(name = "cluster_id")
    private UUID clusterId;

    /**
     * Human-readable display name for the cluster this response belongs to.
     * Null when not yet clustered.
     */
    @Column(name = "cluster_name")
    private String clusterName;

    /**
     * Response data stored as PostgreSQL JSONB.
     * Structure is flexible and determined by the RetroStep's componentType configuration.
     *
     * Common fields:
     * - MULTI_COLUMN_BOARD: category, content
     * - RATING_SCALE: rating, comment
     *
     * Hibernate 6 handles serialization automatically.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> responseData = new HashMap<>();
}
