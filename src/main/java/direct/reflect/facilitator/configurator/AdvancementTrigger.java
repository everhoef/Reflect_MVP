package direct.reflect.facilitator.configurator;

/**
 * Defines when a RetroStep can advance to the next step.
 * Facilitators can always override and advance manually (Next button always enabled with warning).
 */
public enum AdvancementTrigger {
    /**
     * Manual facilitator control - "Next" button click required.
     * Use for steps requiring facilitator discretion (discussion wrap-up, checkpoint reviews).
     */
    FACILITATOR_CLICK,

    /**
     * Wait for all participants to respond before enabling "Next" button.
     * Facilitator can still override and proceed if needed (shows warning: "X participants haven't responded yet").
     * Use for input collection steps where full participation is ideal.
     */
    ALL_RESPONDED,

    /**
     * Automatic advancement after durationSeconds expires.
     * Timer shown to all participants, step advances automatically when time runs out.
     * Use for timed activities (brainstorming, individual reflection).
     */
    TIMER_EXPIRES,

    /**
     * Immediate automatic advancement (no user interaction needed).
     * Step displays and immediately proceeds to next step.
     * Use for transitional messages, quick instructions between activities.
     */
    AUTO
}
