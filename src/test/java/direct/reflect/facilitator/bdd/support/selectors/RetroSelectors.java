package direct.reflect.facilitator.bdd.support.selectors;

public final class RetroSelectors {
    private RetroSelectors() {
    }

    public static final String RETRO_CONTENT = "[data-testid='retro-content']";
    public static final String STAGE_PROGRESS_BAR = "[data-testid='stage-progress-bar']";
    public static final String NEXT_STEP_BUTTON = "[data-testid='next-step-button']";
    public static final String START_RETRO_BUTTON = "[data-testid='start-retro-button']";
    public static final String DISPLAY_NAME_INPUT = "input[name='displayName']";
    public static final String SESSION_NAME_INPUT = "input[name='sessionName']";
    public static final String CREATE_SESSION_BUTTON = "button:has-text('Create Session')";
    public static final String LOGIN_SUBMIT_BUTTON = "button[type='submit']";

    /** Selects the station element for the given 1-based stage ID. Requires data-stage-index attribute on station divs. */
    public static String stationSelector(int stageId) {
        return "[data-stage-index='" + stageId + "']";
    }

    /** Selects the connector element for the given 1-based connector index. Requires data-connector-index attribute. */
    public static String connectorSelector(int connectorIndex) {
        return "[data-connector-index='" + connectorIndex + "']";
    }
}
