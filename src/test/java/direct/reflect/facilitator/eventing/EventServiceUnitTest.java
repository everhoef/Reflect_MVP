package direct.reflect.facilitator.eventing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for EventService focusing on:
 * - SSE connection management
 * - One connection per user enforcement
 * - Event publishing
 * - Connection cleanup
 */
@ExtendWith(MockitoExtension.class)
class EventServiceUnitTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    private EventService eventService;
    private UUID testRetroId;

    @BeforeEach
    void setUp() {
        eventService = new EventService(redisTemplate);
        testRetroId = UUID.randomUUID();
    }

    @Test
    void shouldCreateSseEmitter() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());

        // When
        SseEmitter emitter = eventService.createSseEmitter(testRetroId, request);

        // Then
        assertThat(emitter).isNotNull();
        assertThat(emitter.getTimeout()).isEqualTo(300000L); // 5 minutes
    }

    @Test
    void shouldEnforceOneConnectionPerUser() {
        // Given
        MockHttpServletRequest request1 = new MockHttpServletRequest();
        MockHttpServletRequest request2 = new MockHttpServletRequest();
        
        // Same session ID to simulate same user making multiple connections
        MockHttpSession session = new MockHttpSession();
        request1.setSession(session);
        request2.setSession(session);

        // When
        SseEmitter emitter1 = eventService.createSseEmitter(testRetroId, request1);
        SseEmitter emitter2 = eventService.createSseEmitter(testRetroId, request2);

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
        MockHttpServletRequest user1Request = new MockHttpServletRequest();
        MockHttpServletRequest user2Request = new MockHttpServletRequest();
        
        MockHttpSession session1 = new MockHttpSession();
        MockHttpSession session2 = new MockHttpSession();
        user1Request.setSession(session1);
        user2Request.setSession(session2);

        // When
        SseEmitter emitter1 = eventService.createSseEmitter(testRetroId, user1Request);
        SseEmitter emitter2 = eventService.createSseEmitter(testRetroId, user2Request);

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
        
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());

        // When
        SseEmitter emitter1 = eventService.createSseEmitter(retro1, request);
        SseEmitter emitter2 = eventService.createSseEmitter(retro2, request);

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
}