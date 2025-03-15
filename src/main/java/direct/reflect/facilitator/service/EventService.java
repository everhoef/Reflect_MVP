package direct.reflect.facilitator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.stereotype.Service;

import direct.reflect.facilitator.config.RedisConfig;
import direct.reflect.facilitator.messaging.RetroEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.Disposable;

import java.util.UUID;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {
    private final ReactiveRedisTemplate<String, RetroEvent<?>> reactiveRedisTemplate;
    private Disposable subscription;
    
    @PostConstruct
    public void init() {
        // Set up the pattern subscription that will receive all retro events
        this.subscription = reactiveRedisTemplate
            .listenTo(PatternTopic.of(RedisConfig.ALL_RETROS_PATTERN))
            .doOnNext(message -> {
                if (message instanceof ReactiveSubscription.PatternMessage) {
                    ReactiveSubscription.PatternMessage<?, ?, ?> patternMessage = 
                        (ReactiveSubscription.PatternMessage<?, ?, ?>) message;
                    log.debug("Received message on channel pattern: {}", patternMessage.getPattern());
                } else {
                    log.debug("Received message on unknown channel");
                }
            })
            .subscribe();
    }
    
    @PreDestroy
    public void cleanup() {
        if (subscription != null) {
            subscription.dispose();
        }
    }
    
    public Mono<Long> publish(RetroEvent<?> event) {
        String channel = RedisConfig.getChannelForRetro(event.retroId().toString());
        return reactiveRedisTemplate.convertAndSend(channel, event);
    }
    
    public Flux<RetroEvent<?>> subscribeToRetro(UUID retroId) {
        String channel = RedisConfig.getChannelForRetro(retroId.toString());
        
        return reactiveRedisTemplate
            .listenTo(ChannelTopic.of(channel))
            .map(message -> (RetroEvent<?>) message.getMessage());
    }
    
    public Flux<RetroEvent<?>> subscribeToAllRetros() {
        return reactiveRedisTemplate
            .listenTo(PatternTopic.of(RedisConfig.ALL_RETROS_PATTERN))
            .map(patternMessage -> (RetroEvent<?>) patternMessage.getMessage());
    }
}
