package direct.reflect.facilitator.eventing;

import direct.reflect.facilitator.common.config.RedisConfig;
import direct.reflect.facilitator.eventing.RetroEvent;
import direct.reflect.facilitator.eventing.EventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.connection.stream.RecordId;

import java.util.UUID;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    
    @Mock
    private StreamOperations<String, Object, Object> streamOperations;
    
    @Mock
    private direct.reflect.facilitator.facilitation.ParticipantService participantService;

    @InjectMocks
    private EventService eventService;

    @Test
    void shouldPublishRetroCreatedEventToCorrectStream() {
        // Arrange
        UUID retroId = UUID.randomUUID();
        RetroEvent<Void> event = RetroEvent.retroCreated(retroId, "facilitator1");
        String expectedStreamKey = "retro:stream:" + retroId;
        String expectedRecordId = "1234567890-0";
        
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(streamOperations.add(eq(expectedStreamKey), any(Map.class)))
            .thenReturn(RecordId.of(expectedRecordId));

        // Act
        String result = eventService.publish(event);

        // Assert
        assertThat(result).isEqualTo(expectedRecordId);
        verify(streamOperations).add(eq(expectedStreamKey), any(Map.class));
    }

    @Test
    void shouldPublishDifferentEventsToSameRetroStream() {
        // Arrange
        UUID retroId = UUID.randomUUID();
        String expectedStreamKey = "retro:stream:" + retroId;
        
        RetroEvent<Void> createdEvent = RetroEvent.retroCreated(retroId, "facilitator1");
        RetroEvent<String> phaseEvent = RetroEvent.phaseStarted(retroId, "participant1", "reflection");
        
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(streamOperations.add(eq(expectedStreamKey), any(Map.class)))
            .thenReturn(RecordId.of("1234567890-0"))
            .thenReturn(RecordId.of("1234567891-0"));

        // Act - both events should go to same stream
        String result1 = eventService.publish(createdEvent);
        String result2 = eventService.publish(phaseEvent);

        // Assert
        assertThat(result1).isEqualTo("1234567890-0");
        assertThat(result2).isEqualTo("1234567891-0");
        verify(streamOperations, times(2)).add(eq(expectedStreamKey), any(Map.class));
    }
}