package direct.reflect.facilitator.messaging;

import direct.reflect.facilitator.domain.enums.EventType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RetroEvent<T>(
    UUID retroId,
    EventType type,
    String participantId,
    T payload,
    Instant timestamp,
    // Only used for card-related events to track card lifecycle
    UUID cardId
) {
    // Basic constructor for non-card events
    public RetroEvent(UUID retroId, EventType type, String participantId) {
        this(retroId, type, participantId, null, Instant.now(), null);
    }

    // Constructor for card events
    public RetroEvent(UUID retroId, EventType type, String participantId, T payload, UUID cardId) {
        this(retroId, type, participantId, payload, Instant.now(), cardId);
    }

    // Factory methods for lifecycle events
    public static RetroEvent<Void> retroCreated(UUID retroId, String facilitatorId) {
        return new RetroEvent<>(retroId, EventType.RETRO_CREATED, facilitatorId);
    }

    public static RetroEvent<Void> retroStarted(UUID retroId, String facilitatorId) {
        return new RetroEvent<>(retroId, EventType.RETRO_STARTED, facilitatorId);
    }

    // Factory methods for participant events
    public static RetroEvent<Void> participantJoined(UUID retroId, String participantId) {
        return new RetroEvent<>(retroId, EventType.PARTICIPANT_JOINED, participantId);
    }

    // Factory methods for phase events
    public static RetroEvent<String> phaseStarted(UUID retroId, String facilitatorId, String phase) {
        return new RetroEvent<>(retroId, EventType.PHASE_STARTED, facilitatorId, phase, null);
    }

    // Factory methods for card events (with cardId for correlation)
    public static <T> RetroEvent<T> cardCreated(UUID retroId, String participantId, T cardData, UUID cardId) {
        return new RetroEvent<>(retroId, EventType.CARD_CREATED, participantId, cardData, cardId);
    }

    public static <T> RetroEvent<T> cardUpdated(UUID retroId, String participantId, T cardData, UUID cardId) {
        return new RetroEvent<>(retroId, EventType.CARD_UPDATED, participantId, cardData, cardId);
    }

    // Modifier for payload
    public RetroEvent<T> withPayload(T newPayload) {
        return new RetroEvent<>(retroId, type, participantId, newPayload, timestamp, cardId);
    }
}
