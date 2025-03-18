package direct.reflect.facilitator.messaging;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Event message for retro-related events
 */
public record RetroEvent<T>(
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
        return new RetroEvent<>(retroId, EventType.PARTICIPANT_JOINED, "system", Instant.now(), displayName);
    }
    
    /**
     * Create a session started event
     */
    public static RetroEvent<Void> sessionStarted(UUID retroId) {
        return new RetroEvent<>(retroId, EventType.SESSION_STARTED, "system", Instant.now(), null);
    }
    
    /**
     * Create a step advanced event
     */
    public static RetroEvent<Void> stepAdvanced(UUID retroId) {
        return new RetroEvent<>(retroId, EventType.STEP_ADVANCED, "system", Instant.now(), null);
    }
    
    /**
     * Create a retro created event
     */
    public static RetroEvent<Void> retroCreated(UUID retroId, String facilitatorId) {
        return new RetroEvent<>(retroId, EventType.RETRO_CREATED, facilitatorId, Instant.now(), null);
    }
    
    /**
     * Create a phase started event
     */
    public static RetroEvent<String> phaseStarted(UUID retroId, String facilitatorId, String phaseName) {
        return new RetroEvent<>(retroId, EventType.PHASE_STARTED, facilitatorId, Instant.now(), phaseName);
    }
}
