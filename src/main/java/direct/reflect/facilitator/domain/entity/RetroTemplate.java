package direct.reflect.facilitator.domain.entity;

import jakarta.persistence.*;

import direct.reflect.facilitator.domain.enums.RetroPhase;
import lombok.Data;

@Entity
@Data
public class RetroTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    
    private String name;
    private String description;
    private int maturityLevel;  // 1-5 indicating complexity/experience needed
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "set_stage_id", nullable = false)
    private RetroStage setTheStage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gather_data_id", nullable = false)
    private RetroStage gatherData;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generate_insights_id", nullable = false)
    private RetroStage generateInsights;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decide_actions_id", nullable = false)
    private RetroStage decideActions;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "close_retro_id", nullable = false)
    private RetroStage closeRetro;

    public RetroStage getStageForPhase(RetroPhase phase) {
        return switch(phase) {
                    case SET_THE_STAGE -> setTheStage;
                    case GATHER_DATA -> gatherData;
                    case GENERATE_INSIGHTS -> generateInsights;
                    case DECIDE_ACTIONS -> decideActions;
                    case CLOSE_RETRO -> closeRetro;
                    default -> throw new IllegalArgumentException("Unexpected value: " + phase);
        };
    }
}
