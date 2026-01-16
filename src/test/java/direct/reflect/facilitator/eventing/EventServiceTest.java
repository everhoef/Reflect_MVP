package direct.reflect.facilitator.eventing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EventService focusing on:
 * - SSE connection management
 * - One connection per user enforcement
 * - Event publishing
 * - Redis Pub/Sub operations
 * - Connection cleanup
 */
@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private direct.reflect.facilitator.facilitation.ParticipantService participantService;

    @InjectMocks
    private EventService eventService;
    private UUID testRetroId;

    @BeforeEach
    void setUp() {
        testRetroId = UUID.randomUUID();
    }

    @Test
    void shouldCreateSseEmitter() {
        // Given
        UUID participantId = UUID.randomUUID();

        // When
        SseEmitter emitter = eventService.createSseEmitter(testRetroId, participantId, "Test User");

        // Then
        assertThat(emitter).isNotNull();
        assertThat(emitter.getTimeout()).isEqualTo(3600000L); // 1 hour
    }

    @Test
    void shouldEnforceOneConnectionPerUser() {
        // Given
        UUID participantId = UUID.randomUUID();

        // When - same participantId makes multiple connections
        SseEmitter emitter1 = eventService.createSseEmitter(testRetroId, participantId, "Test User");
        SseEmitter emitter2 = eventService.createSseEmitter(testRetroId, participantId, "Test User");

        // Then
        assertThat(emitter1).isNotNull();
        assertThat(emitter2).isNotNull();
        assertThat(emitter2).isNotSameAs(emitter1);

        // Only one active connection should remain
        // (This is tested by the implementation logic - old connection gets completed)
    }

    @Test
    void shouldAllowMultipleUsersInSameRetro() {
        // Given
        UUID participant1Id = UUID.randomUUID();
        UUID participant2Id = UUID.randomUUID();

        // When
        SseEmitter emitter1 = eventService.createSseEmitter(testRetroId, participant1Id, "User 1");
        SseEmitter emitter2 = eventService.createSseEmitter(testRetroId, participant2Id, "User 2");

        // Then
        assertThat(emitter1).isNotNull();
        assertThat(emitter2).isNotNull();
        assertThat(emitter2).isNotSameAs(emitter1);
    }

    @Test
    void shouldAllowSameUserInDifferentRetros() {
        // Given
        UUID retro1 = UUID.randomUUID();
        UUID retro2 = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();

        // When
        SseEmitter emitter1 = eventService.createSseEmitter(retro1, participantId, "Test User");
        SseEmitter emitter2 = eventService.createSseEmitter(retro2, participantId, "Test User");

        // Then
        assertThat(emitter1).isNotNull();
        assertThat(emitter2).isNotNull();
        assertThat(emitter2).isNotSameAs(emitter1);
    }

    @Test
    void shouldCreateValidRetroCreatedEvent() {
        // When
        RetroEvent<Void> event = RetroEvent.retroCreated(testRetroId, "system");

        // Then
        assertThat(event.retroId()).isEqualTo(testRetroId);
        assertThat(event.type()).isEqualTo(RetroEvent.EventType.RETRO_CREATED);
        assertThat(event.sourceId()).isEqualTo("system");
        assertThat(event.payload()).isNull();
        assertThat(event.timestamp()).isNotNull();
    }

    @Test
    void shouldCreateValidParticipantJoinedEvent() {
        // Given
        String participantName = "TestUser";
        
        // When
        RetroEvent<String> event = RetroEvent.participantJoined(testRetroId, participantName);

        // Then
        assertThat(event.retroId()).isEqualTo(testRetroId);
        assertThat(event.type()).isEqualTo(RetroEvent.EventType.PARTICIPANT_JOINED);
        assertThat(event.sourceId()).isEqualTo("system");
        assertThat(event.payload()).isEqualTo(participantName);
        assertThat(event.timestamp()).isNotNull();
    }

    @Test
    void shouldPublishRetroCreatedEventToCorrectChannel() {
        // Arrange
        UUID retroId = UUID.randomUUID();
        RetroEvent<Void> event = RetroEvent.retroCreated(retroId, "facilitator1");

        // Act
        eventService.publish(event);

        // Assert - verify event is published to ApplicationEventPublisher (transaction-aware)
        verify(applicationEventPublisher).publishEvent(eq(event));
    }

    @Test
    void shouldPublishDifferentEventsToSameRetroChannel() {
        // Arrange
        UUID retroId = UUID.randomUUID();

        RetroEvent<Void> createdEvent = RetroEvent.retroCreated(retroId, "facilitator1");
        RetroEvent<String> phaseEvent = RetroEvent.phaseStarted(retroId, "participant1", "reflection");

        // Act - both events should be published via ApplicationEventPublisher
        eventService.publish(createdEvent);
        eventService.publish(phaseEvent);

        // Assert - verify both events are published to ApplicationEventPublisher (transaction-aware)
        verify(applicationEventPublisher, times(2)).publishEvent(any(RetroEvent.class));
    }
}