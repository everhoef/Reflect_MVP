package direct.reflect.facilitator.eventing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.stereotype.Service;

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
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    
    // Local SSE connections for this pod instance
    private final ConcurrentHashMap<String, SseEmitter> localEmitters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService keepAliveExecutor = Executors.newScheduledThreadPool(2);
    
    private static final String STREAM_KEY_PREFIX = "retro:stream:";
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
        log.info("Publishing {} event for retro {} with payload: {}", 
            event.type(), event.retroId(), event.payload());
            
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
     * Ensures only one connection per user per retro.
     */
    public SseEmitter createSseEmitter(UUID retroId, HttpServletRequest request) {
        String connectionId = retroId + ":" + request.getSession().getId();
        
        // Check if connection already exists - close the old one first
        SseEmitter existingEmitter = localEmitters.get(connectionId);
        if (existingEmitter != null) {
            log.info("Closing existing SSE connection for: {}", connectionId);
            cleanupConnection(connectionId, existingEmitter);
        }
        
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        
        // Store locally
        localEmitters.put(connectionId, emitter);
        activeConnections.incrementAndGet();
        
        // Cleanup on close/error/timeout - use a single cleanup method to avoid race conditions
        emitter.onCompletion(() -> {
            cleanupConnection(connectionId, emitter);
            log.info("SSE connection completed for retro: {}", retroId);
        });
        
        emitter.onTimeout(() -> {
            cleanupConnection(connectionId, emitter);
            log.warn("SSE connection timed out for retro: {}", retroId);
        });
        
        emitter.onError((error) -> {
            cleanupConnection(connectionId, emitter);
            log.error("SSE connection error for retro: {}: {}", retroId, error.getMessage());
        });
        
        log.info("Created SSE emitter for retro: {} (active connections: {})", retroId, activeConnections.get());
        
        // Send immediate connection confirmation event
        try {
            emitter.send(SseEmitter.event()
                .name("connection_established")
                .data("SSE connection established")
                .comment("Connection confirmed"));
            log.debug("Sent connection_established event for retro: {}", retroId);
        } catch (IOException e) {
            log.warn("Failed to send connection_established event: {}", e.getMessage());
            // Don't cleanup here - the error handler will be called
        }
        
        return emitter;
    }
    
    /**
     * Thread-safe cleanup method to avoid double-decrements.
     */
    private void cleanupConnection(String connectionId, SseEmitter emitter) {
        // Only cleanup if the emitter in the map matches the one we're cleaning up
        // This prevents race conditions where multiple cleanup calls happen for the same connection
        if (localEmitters.remove(connectionId, emitter)) {
            activeConnections.decrementAndGet();
            log.debug("Cleaned up SSE connection: {} (active: {})", connectionId, activeConnections.get());
            try {
                emitter.complete();
            } catch (Exception e) {
                // Ignore completion errors during cleanup
                log.debug("Error completing emitter during cleanup: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Send to local emitters.
     */
    private void sendToLocalEmitters(UUID retroId, RetroEvent<?> event) {
        String connectionId = retroId + ":";
        
        List<String> matchingConnections = localEmitters.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(connectionId))
            .map(entry -> entry.getKey())
            .toList();
            
        log.info("Sending {} event to {} connections for retro {}", 
            event.type(), matchingConnections.size(), retroId);
        
        // Take a snapshot of matching emitters to avoid concurrent modification
        List<Map.Entry<String, SseEmitter>> matchingEmitters = localEmitters.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(connectionId))
            .toList();
            
        matchingEmitters.forEach(entry -> {
            try {
                log.debug("Sending {} event to connection: {}", event.type(), entry.getKey());
                entry.getValue().send(SseEmitter.event()
                    .name(event.type().name().toLowerCase())
                    .data(event));
            } catch (IOException e) {
                log.warn("Failed to send event to connection: {} - {}", entry.getKey(), e.getMessage());
                // Don't manually cleanup here - let the emitter's error handler deal with it
                // This prevents double-cleanup and negative connection counts
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
        
        // Take a snapshot to avoid concurrent modification
        List<Map.Entry<String, SseEmitter>> currentEmitters = new ArrayList<>(localEmitters.entrySet());
        
        currentEmitters.forEach(entry -> {
            try {
                entry.getValue().send(SseEmitter.event()
                    .name("keep-alive")
                    .data("heartbeat")
                    .comment("Keep connection alive"));
            } catch (IOException e) {
                log.warn("Keep-alive failed for connection: {} - {}", entry.getKey(), e.getMessage());
                // Don't manually cleanup here - let the emitter's error handler deal with it
                // This prevents double-cleanup and negative connection counts
            }
        });
    }
    
}
