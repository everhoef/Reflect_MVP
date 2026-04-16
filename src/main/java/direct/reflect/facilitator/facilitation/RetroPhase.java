package direct.reflect.facilitator.facilitation;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RetroPhase {
    CREATED("Creating Retro", "Initial state"),
    LOBBY("Lobby", "Waiting for participants"),
    SET_THE_STAGE("Set The Stage", "Setting context and expectations"),
    GATHER_DATA("Gather Data", "Collecting information and experiences"),
    GENERATE_INSIGHTS("Generate Insights", "Finding patterns and meaning"),
    DECIDE_ACTIONS("Decide What To Do", "Planning concrete improvements"),
    CLOSE_RETRO("Close The Retrospective", "Wrapping up and feedback"),
    PAUSED("Paused", "Temporarily halted"),
    COMPLETED("Well done!", "Retrospective finished"),
    ABANDONED("Abandoned", "The retrospective was started but not completed.");

    private final String displayName;
    private final String description;

    public RetroPhase next() {
        return switch(this) {
            case CREATED -> LOBBY;
            case LOBBY -> SET_THE_STAGE;
            case SET_THE_STAGE -> GATHER_DATA;
            case GATHER_DATA -> GENERATE_INSIGHTS;
            case GENERATE_INSIGHTS -> DECIDE_ACTIONS;
            case DECIDE_ACTIONS -> CLOSE_RETRO;
            case CLOSE_RETRO -> COMPLETED;
            default -> throw new IllegalStateException("Unexpected value: " + this);
        };
    }

    /**
     * Check if this phase allows participants to submit responses.
     * Active phases are those where retrospective activities are in progress.
     */
    public boolean isActivePhase() {
        return switch(this) {
            case SET_THE_STAGE, GATHER_DATA, GENERATE_INSIGHTS, DECIDE_ACTIONS, CLOSE_RETRO -> true;
            case CREATED, LOBBY, PAUSED, COMPLETED, ABANDONED -> false;
        };
    }
}
