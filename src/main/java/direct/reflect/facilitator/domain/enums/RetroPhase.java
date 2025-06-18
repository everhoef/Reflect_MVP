package direct.reflect.facilitator.domain.enums;

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
    ABANDONED("Abandoned", "Retrospective abandoned");

    private final String displayName;
    private final String description;

    RetroPhase(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

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
}
