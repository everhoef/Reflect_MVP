package direct.reflect.facilitator.service;

import direct.reflect.facilitator.config.RedisConfig;
import direct.reflect.facilitator.messaging.RetroEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private ReactiveRedisTemplate<String, RetroEvent<?>> reactiveRedisTemplate;

    @InjectMocks
    private EventService eventService;

    @Test
    void shouldPublishRetroCreatedEventToCorrectChannel() {
        // Arrange
        UUID retroId = UUID.randomUUID();
        RetroEvent<Void> event = RetroEvent.retroCreated(retroId, "facilitator1");
        String expectedChannel = RedisConfig.getChannelForRetro(retroId.toString());
        
        when(reactiveRedisTemplate.convertAndSend(eq(expectedChannel), eq(event)))
            .thenReturn(Mono.just(1L));

        // Act & Assert
        StepVerifier.create(eventService.publish(event))
            .expectNext(1L)
            .verifyComplete();

        verify(reactiveRedisTemplate).convertAndSend(expectedChannel, event);
    }

    @Test
    void shouldPublishDifferentEventsToSameRetroChannel() {
        // Arrange
        UUID retroId = UUID.randomUUID();
        String expectedChannel = RedisConfig.getChannelForRetro(retroId.toString());
        
        RetroEvent<Void> createdEvent = RetroEvent.retroCreated(retroId, "facilitator1");
        RetroEvent<String> phaseEvent = RetroEvent.phaseStarted(retroId, "participant1", "reflection");
        
        when(reactiveRedisTemplate.convertAndSend(eq(expectedChannel), eq(createdEvent)))
            .thenReturn(Mono.just(1L));
        when(reactiveRedisTemplate.convertAndSend(eq(expectedChannel), eq(phaseEvent)))
            .thenReturn(Mono.just(1L));

        // Act & Assert - both events should go to same channel
        StepVerifier.create(eventService.publish(createdEvent))
            .expectNext(1L)
            .verifyComplete();
        StepVerifier.create(eventService.publish(phaseEvent))
            .expectNext(1L)
            .verifyComplete();

        verify(reactiveRedisTemplate).convertAndSend(expectedChannel, createdEvent);
        verify(reactiveRedisTemplate).convertAndSend(expectedChannel, phaseEvent);
    }
}