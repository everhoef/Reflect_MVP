package direct.reflect.facilitator.configurator;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Data;

@Entity
@Data
public class RetroTemplate {
    /** Database identifier. */
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    /** Display name used in template selection. */
    private String name;

    /** Human-readable template description. */
    private String description;

    /** Relative complexity from 1 to 5. */
    private int maturityLevel;  // 1-5 indicating complexity/experience needed

    /** Whether the template can be selected in the app. */
    private boolean isReleased;

    /** Stage used for the set-the-stage phase. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "set_stage_id", nullable = false)
    private RetroStage setTheStage;

    /** Stage used for the gather-data phase. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gather_data_id", nullable = false)
    private RetroStage gatherData;

    /** Stage used for the generate-insights phase. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generate_insights_id", nullable = false)
    private RetroStage generateInsights;

    /** Stage used for the decide-actions phase. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decide_actions_id", nullable = false)
    private RetroStage decideActions;

    /** Stage used for the close-retro phase. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "close_retro_id", nullable = false)
    private RetroStage closeRetro;
}
