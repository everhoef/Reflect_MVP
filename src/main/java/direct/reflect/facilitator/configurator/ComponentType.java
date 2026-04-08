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
     * ESVP single-select category component.
     * Configuration: columns[] (id, title, emoji, color), capabilities{allowInput, showContent}
     * Use cases: ESVP check-in (Explorer/Shopper/Vacationer/Prisoner)
     * Each participant selects exactly one category; reveal shows aggregated counts per category.
     */
    ESVP_SELECTOR
}
