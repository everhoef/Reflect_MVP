package direct.reflect.facilitator.eventing;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Event message for retro-related events
 */
public record RetroEvent<T>(
    String correlationId,
    UUID retroId,
    EventType type,
    String sourceId,
    Instant timestamp,
    T payload
) {
    /**
     * Enum of possible event types
     */
    public enum EventType {
        PARTICIPANT_JOINED,
        PARTICIPANT_LEFT,
        SESSION_STARTED,
        STEP_ADVANCED,
        RETRO_CREATED,
        PHASE_STARTED,
        NOTE_ADDED,
        NOTE_UPDATED,
        NOTE_DELETED,
        VOTE_ADDED,
        VOTE_REMOVED,
        GROUP_CREATED,
        GROUP_UPDATED,
        GROUP_DELETED,
        ACTION_CREATED,
        ACTION_UPDATED,
        ACTION_DELETED,
        TIMER_STARTED,
        TIMER_PAUSED,
        TIMER_FINISHED
    }

    /**
     * Create a participant joined event
     */
    public static RetroEvent<String> participantJoined(UUID retroId, String displayName) {
        return new RetroEvent<>("evt-" + UUID.randomUUID().toString().substring(0, 8), retroId, EventType.PARTICIPANT_JOINED, "system", Instant.now(), displayName);
    }
    
    /**
     * Create a participant left event
     */
    public static RetroEvent<String> participantLeft(UUID retroId, String displayName) {
        return new RetroEvent<>("evt-" + UUID.randomUUID().toString().substring(0, 8), retroId, EventType.PARTICIPANT_LEFT, "system", Instant.now(), displayName);
    }

    /**
     * Create a session started event
     */
    public static RetroEvent<Void> sessionStarted(UUID retroId) {
        return new RetroEvent<>("evt-" + UUID.randomUUID().toString().substring(0, 8), retroId, EventType.SESSION_STARTED, "system", Instant.now(), null);
    }

    /**
     * Create a step advanced event
     */
    public static RetroEvent<Void> stepAdvanced(UUID retroId) {
        return new RetroEvent<>("evt-" + UUID.randomUUID().toString().substring(0, 8), retroId, EventType.STEP_ADVANCED, "system", Instant.now(), null);
    }

    /**
     * Create a retro created event
     */
    public static RetroEvent<Void> retroCreated(UUID retroId, String facilitatorId) {
        return new RetroEvent<>("evt-" + UUID.randomUUID().toString().substring(0, 8), retroId, EventType.RETRO_CREATED, facilitatorId, Instant.now(), null);
    }

    /**
     * Create a phase started event
     */
    public static RetroEvent<String> phaseStarted(UUID retroId, String facilitatorId, String phaseName) {
        return new RetroEvent<>("evt-" + UUID.randomUUID().toString().substring(0, 8), retroId, EventType.PHASE_STARTED, facilitatorId, Instant.now(), phaseName);
    }
    
    /**
     * Response data for NOTE events (responses)
     */
    public record ResponseData(
        String responseId,
        Long stepId,
        String participantId,
        String participantName,
        String displaySummary,
        boolean isVisible,
        Instant submittedAt
    ) {}
    
    /**
     * Create a response submitted event (NOTE_ADDED)
     */
    public static RetroEvent<ResponseData> responseSubmitted(UUID retroId, String participantId, ResponseData responseData) {
        return new RetroEvent<>("evt-" + UUID.randomUUID().toString().substring(0, 8), retroId, EventType.NOTE_ADDED, participantId, Instant.now(), responseData);
    }

    /**
     * Create a response privacy changed event (NOTE_UPDATED)
     */
    public static RetroEvent<ResponseData> responsePrivacyChanged(UUID retroId, String facilitatorId, ResponseData responseData) {
        return new RetroEvent<>("evt-" + UUID.randomUUID().toString().substring(0, 8), retroId, EventType.NOTE_UPDATED, facilitatorId, Instant.now(), responseData);
    }

    /**
     * Create responses revealed event (batch privacy change)
     */
    public static RetroEvent<Long> responsesRevealed(UUID retroId, String facilitatorId, Long stepId) {
        return new RetroEvent<>("evt-" + UUID.randomUUID().toString().substring(0, 8), retroId, EventType.NOTE_UPDATED, facilitatorId, Instant.now(), stepId);
    }

    /**
     * Create a timer paused event
     */
    public static RetroEvent<Void> timerPaused(UUID retroId) {
        return new RetroEvent<>("evt-" + UUID.randomUUID().toString().substring(0, 8), retroId, EventType.TIMER_PAUSED, "system", Instant.now(), null);
    }

    /**
     * Create a timer started/resumed event (use TIMER_STARTED for both start and resume)
     */
    public static RetroEvent<Void> timerStarted(UUID retroId) {
        return new RetroEvent<>("evt-" + UUID.randomUUID().toString().substring(0, 8), retroId, EventType.TIMER_STARTED, "system", Instant.now(), null);
    }
}
