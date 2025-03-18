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
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {
    private final ReactiveRedisTemplate<String, RetroEvent<?>> reactiveRedisTemplate;
    private final AtomicInteger activeConnections = new AtomicInteger(0);
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
    
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Flux<RetroEvent<?>> subscribeToRetro(UUID retroId) {
        String channel = RedisConfig.getChannelForRetro(retroId.toString());
        
        int currentConnections = activeConnections.incrementAndGet();
        log.info("New SSE connection for retro {}, active connections: {}", retroId, currentConnections);
        
        // Use cast to ensure compatible types - the <? extends Object> fixes the incompatible types error
        return (Flux) reactiveRedisTemplate
            .<RetroEvent<?>>listenTo(ChannelTopic.of(channel))
            .map(message -> {
                Object msg = message.getMessage();
                if (msg instanceof RetroEvent) {
                    return msg;
                }
                log.warn("Received message of unexpected type: {}", msg != null ? msg.getClass() : "null");
                return null;
            })
            .filter(event -> event != null)
            .doOnCancel(() -> {
                activeConnections.decrementAndGet();
                log.info("SSE connection closed for retro {}", retroId);
            });
    }
    
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Flux<RetroEvent<?>> subscribeToAllRetros() {
        // Use cast to ensure compatible types
        return (Flux) reactiveRedisTemplate
            .<RetroEvent<?>>listenTo(PatternTopic.of(RedisConfig.ALL_RETROS_PATTERN))
            .map(patternMessage -> patternMessage.getMessage());
    }
}
