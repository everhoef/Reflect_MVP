package direct.reflect.facilitator.configurator;

public enum StepType {
    INSTRUCTION("INSTRUCTION"),  // Facilitator explanation, shown in LEFT section
    ACTIVITY("ACTION"),          // Participant interaction, shown in CENTER section (displayed as ACTION to users)
    DISCUSSION("DISCUSSION");    // Results + comments, shown in CENTER + RIGHT sections

    private final String displayName;

    StepType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}