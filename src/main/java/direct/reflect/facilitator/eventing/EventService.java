package direct.reflect.facilitator.eventing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.stereotype.Service;

import direct.reflect.facilitator.eventing.RetroEvent;
import direct.reflect.facilitator.facilitation.ParticipantService;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.time.LocalDateTime;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {
    private final RedisTemplate<String, Object> redisTemplate;
    private final ParticipantService participantService;
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    
    // Local SSE connections for this pod instance
    private final ConcurrentHashMap<String, SseEmitter> localEmitters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService keepAliveExecutor = Executors.newScheduledThreadPool(2);
    
    private static final String STREAM_KEY_PREFIX = "retro:stream:";
    private static final String CONSUMER_GROUP = "retro-events";
    private static final long SSE_TIMEOUT = 300000L; // 5 minutes
    
    @PostConstruct
    public void init() {
        // Initialize Redis Streams consumer groups for each retro
        // This will be called per retro when first accessed
        log.info("EventService initialized with Redis Streams support");
        
        // Start keep-alive scheduler
        keepAliveExecutor.scheduleAtFixedRate(this::sendKeepAlive, 30, 30, TimeUnit.SECONDS);
    }
    
    @PreDestroy
    public void cleanup() {
        // Close all local SSE connections
        localEmitters.values().forEach(emitter -> {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.warn("Error closing SSE emitter: {}", e.getMessage());
            }
        });
        keepAliveExecutor.shutdown();
    }
    
    /**
     * Publish event to Redis Stream (blocking).
     */
    public String publish(RetroEvent<?> event) {
        String streamKey = STREAM_KEY_PREFIX + event.retroId();
        
        // Create stream record
        Map<String, Object> eventData = Map.of(
            "type", event.type().name(),
            "retroId", event.retroId().toString(),
            "payload", event.payload() != null ? event.payload() : "",
            "timestamp", LocalDateTime.now().toString()
        );
        
        // Add to Redis Stream (blocking)
        RecordId recordId = redisTemplate.opsForStream().add(streamKey, eventData);
        
        // Send to all local SSE connections for this retro
        sendToLocalEmitters(event.retroId(), event);
        
        return recordId.getValue();
    }
    
    /**
     * Create SSE emitter (simple blocking approach).
     */
    public SseEmitter createSseEmitter(UUID retroId, HttpServletRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        String connectionId = retroId + ":" + request.getSession().getId();
        
        // Store locally
        localEmitters.put(connectionId, emitter);
        activeConnections.incrementAndGet();
        
        // Cleanup on close/error/timeout
        emitter.onCompletion(() -> {
            localEmitters.remove(connectionId);
            activeConnections.decrementAndGet();
            log.info("SSE connection completed for retro: {}", retroId);
        });
        
        emitter.onTimeout(() -> {
            localEmitters.remove(connectionId);
            activeConnections.decrementAndGet();
            log.warn("SSE connection timed out for retro: {}", retroId);
        });
        
        emitter.onError((error) -> {
            localEmitters.remove(connectionId);
            activeConnections.decrementAndGet();
            log.error("SSE connection error for retro: {}: {}", retroId, error.getMessage());
        });
        
        log.info("Created SSE emitter for retro: {} (active connections: {})", retroId, activeConnections.get());
        return emitter;
    }
    
    /**
     * Send to local emitters.
     */
    private void sendToLocalEmitters(UUID retroId, RetroEvent<?> event) {
        String connectionId = retroId + ":";
        localEmitters.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(connectionId))
            .forEach(entry -> {
                try {
                    entry.getValue().send(SseEmitter.event()
                        .name(event.type().name().toLowerCase())
                        .data(event));
                } catch (IOException e) {
                    String removedKey = entry.getKey();
                    localEmitters.remove(removedKey);
                    activeConnections.decrementAndGet();
                    log.warn("Removed broken SSE connection: {}", removedKey);
                }
            });
    }
    
    /**
     * Send keep-alive messages to all active connections.
     */
    private void sendKeepAlive() {
        if (localEmitters.isEmpty()) {
            return;
        }
        
        List<String> brokenConnections = new ArrayList<>();
        localEmitters.forEach((connectionId, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                    .name("keep-alive")
                    .data("heartbeat")
                    .comment("Keep connection alive"));
            } catch (IOException e) {
                brokenConnections.add(connectionId);
                log.warn("Keep-alive failed for connection: {}", connectionId);
            }
        });
        
        // Remove broken connections
        brokenConnections.forEach(connectionId -> {
            localEmitters.remove(connectionId);
            activeConnections.decrementAndGet();
        });
        
        if (!brokenConnections.isEmpty()) {
            log.info("Removed {} broken connections during keep-alive. Active connections: {}", 
                brokenConnections.size(), activeConnections.get());
        }
    }
    
}
