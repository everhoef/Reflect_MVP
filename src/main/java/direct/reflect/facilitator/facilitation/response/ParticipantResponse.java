package direct.reflect.facilitator.facilitation.response;

import jakarta.persistence.*;
import lombok.Data;
import direct.reflect.facilitator.facilitation.Participant;
import direct.reflect.facilitator.configurator.RetroStep;
import direct.reflect.facilitator.configurator.DataPattern;
import direct.reflect.facilitator.common.entity.GeneratedUuidV7;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base class for all participant responses using Single Table Inheritance.
 *
 * Design rationale:
 * - Single table for easier data analysis and cross-pattern queries (POC requirement)
 * - Type-safe subclasses prevent invalid field combinations
 * - Discriminator column enables polymorphic queries
 * - Supports clustering, voting, and visibility controls
 *
 * Subclasses:
 * - CategoricalResponse: Sticky notes with categories (Mad/Sad/Glad, Start/Stop/Continue)
 * - RatingResponse: Numeric ratings with optional comments (Happiness Histogram, ROTI)
 * - FreeformResponse: Open text responses (One Word, Kudos, Closing Statements)
 */
@Entity
@Table(name = "participant_responses")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "response_type", discriminatorType = DiscriminatorType.STRING)
@Data
public abstract class ParticipantResponse {

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
     * Get the data pattern for this response (determined by subclass type).
     */
    @Transient
    public abstract DataPattern getDataPattern();

    /**
     * Get a display-friendly summary of this response.
     */
    @Transient
    public abstract String getDisplaySummary();
}
