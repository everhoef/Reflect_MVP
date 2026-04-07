package direct.reflect.facilitator.configurator;

/**
 * Defines the type of UI component to render for a RetroStep.
 * Each component reads its configuration from the step's componentConfig JSON field.
 */
public enum ComponentType {
    /**
     * Multi-column board for card-based activities.
     * Supports 1+ columns (1 column = freeform list, 2+ = categorized columns).
     * Configuration: columns[], capabilities{allowInput, allowVoting, allowMerging}, cardConfig{maxLength, placeholder, showVotes, showAuthor}
     * Use cases: Mad/Sad/Glad (3 cols), Start/Stop/Continue (3 cols), ESVP (4 cols), Kudos (1 col)
     */
    MULTI_COLUMN_BOARD,

    /**
     * Numeric rating input component.
     * Configuration: min, max, step, labels[], allowComment, commentMaxLength
     * Use cases: Happiness Histogram, ROTI, satisfaction scales
     */
    RATING_SCALE,

    /**
     * Rating distribution visualization (histogram chart).
     * Configuration: min, max, showComments, groupBy
     * Use cases: Display results for RATING_SCALE activities
     */
    HISTOGRAM_CHART,

    /**
     * Simple message for guidance or information.
     * Configuration: title, message, variant
     * Use cases: Welcome screens, instructions
     */
    GUIDANCE_MESSAGE,

    /**
     * Visual layout for media or static content.
     * Configuration: layoutType, contentUrl
     * Use cases: Video check-ins, image displays
     */
    VISUAL_LAYOUT,

    /**
     * Guided UI for building SMART action points.
     * Configuration: maxLength, templates[], categories[]
     * Use cases: Decide Actions phase
     */
    SMART_ACTION_BUILDER,

    /**
     * Review list for created action points.
     * Configuration: allowEdit, allowDelete, showStatus
     * Use cases: Close Retro phase, summary screens
     */
    ACTION_REVIEW
}
