package direct.reflect.facilitator.configurator;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.Map;
import java.util.HashMap;

/**
 * Simplified RetroStep entity following Wizard Pattern architecture.
 * Each step defines WHAT to display (componentType + componentConfig) and WHEN to advance (advancementTrigger).
 */
@Entity
@Data
public class RetroStep {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "retro_stage_id", nullable = false)
    private RetroStage retroStage;

    @Column(nullable = false)
    private Integer orderIndex;

    // ========== WHAT TO DISPLAY ==========

    /**
     * Defines which UI component to render for this step.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ComponentType componentType;

    /**
     * Pure JSON configuration for the component stored as PostgreSQL JSONB.
     * Structure depends on componentType (e.g., columns[] for MULTI_COLUMN_BOARD, min/max for RATING_SCALE).
     * Hibernate 6 automatically handles JSON serialization/deserialization to Map<String, Object>.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> componentConfig = new HashMap<>();

    /**
     * Instructions text (displayed in chatbox).
     * Provides step-by-step instructions. Chatbox shows last N messages from current stage.
     */
    @Column(columnDefinition = "TEXT")
    private String instructions;

    // ========== WHEN TO ADVANCE ==========

    /**
     * Defines when this step can advance to the next step.
     * Facilitator can always override (Next button always enabled with warnings).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AdvancementTrigger advancementTrigger = AdvancementTrigger.FACILITATOR_CLICK;

    /**
     * Duration in seconds for TIMER_EXPIRES advancement trigger.
     * 0 or null = no timer, positive value = timer duration.
     */
    @Column(nullable = false)
    private Integer durationSeconds = 0;

    /**
     * Get component configuration for template access.
     * Templates can read fields like: ${step.config.columns}, ${step.config.capabilities.allowInput}, etc.
     * Hibernate handles JSONB automatically, no parsing needed.
     */
    public Map<String, Object> getConfig() {
        return componentConfig != null ? componentConfig : Map.of();
    }
}
