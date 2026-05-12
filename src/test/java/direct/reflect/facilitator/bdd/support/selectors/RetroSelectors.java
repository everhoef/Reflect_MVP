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
    public static final String JOIN_RETRO_ID_INPUT = "input[name='retroId']";
    public static final String CREATE_SESSION_BUTTON = "button:has-text('Create Session')";
    public static final String JOIN_SESSION_BUTTON = "button:has-text('Join Session')";
    public static final String LOGIN_SUBMIT_BUTTON = "button[type='submit']";
    public static final String COLUMN_BOARD_ITEM = "[data-testid^='column-']";
    public static final String ERROR_PAGE_MESSAGE = "p:text-is('Retrospective not found'), p:text-is('Could not load retrospective')";
    public static final String OWN_NOTE_EDITABLE = RETRO_CONTENT + " p[title='Click to edit']";

    // ============================================================
    // Access control selectors — login/personal info fields
    // ============================================================
    /** Password input field */
    public static final String PASSWORD_INPUT = "input[type='password']";
    /** Email input field */
    public static final String EMAIL_INPUT = "input[type='email']";
    /** Username input field */
    public static final String USERNAME_INPUT = "input[name='username']";
    /** CAPTCHA input field */
    public static final String CAPTCHA_INPUT = "input[placeholder*='CAPTCHA' i]";
    /** Phone placeholder input field */
    public static final String PHONE_PLACEHOLDER_INPUT = "input[placeholder*='phone' i]";
    /** Address placeholder input field */
    public static final String ADDRESS_PLACEHOLDER_INPUT = "input[placeholder*='address' i]";
    /** Phone-named input field */
    public static final String PHONE_NAME_INPUT = "input[name='phone']";
    /** Address-named input field */
    public static final String ADDRESS_NAME_INPUT = "input[name='address']";
    /** Telephone input field */
    public static final String TELEPHONE_INPUT = "input[type='tel']";

    // ============================================================
    // Error page assertions
    // ============================================================
    /** Asserts 'Retrospective not found' message is present */
    public static final String ERROR_PAGE_NOT_FOUND_MESSAGE = "p:text-is('Retrospective not found')";
    /** Asserts 'Could not load retrospective' message is present */
    public static final String ERROR_PAGE_LOAD_FAILED_MESSAGE = "p:text-is('Could not load retrospective')";
    /** Asserts 'The session may have ended or the link is invalid' message is present */
    public static final String ERROR_PAGE_SESSION_ENDED_MESSAGE = "p:text-is('The session may have ended or the link is invalid.')";

    /** Selects the station element for the given 1-based stage ID. Requires data-stage-index attribute on station divs. */
    public static String stationSelector(int stageId) {
        return "[data-stage-index='" + stageId + "']";
    }

    /** Selects the connector element for the given 1-based connector index. Requires data-connector-index attribute. */
    public static String connectorSelector(int connectorIndex) {
        return "[data-connector-index='" + connectorIndex + "']";
    }

    public static String noteInputSelector(String columnId) {
        return "[data-testid='note-input-" + columnId + "']";
    }

    public static String noteSubmitSelector(String columnId) {
        return "[data-testid='submit-note-" + columnId + "']";
    }

    public static String noteTextSelector(String noteContent) {
        return "text=" + noteContent;
    }

    public static String authorTextSelector(String displayName) {
        return "text=" + displayName;
    }
}
