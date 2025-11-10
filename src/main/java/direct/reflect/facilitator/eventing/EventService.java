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
    private static final long SSE_TIMEOUT = 3600000L; // 1 hour
    
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
        log.debug("Publishing {} event for retro {}", event.type(), event.retroId());

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
    public SseEmitter createSseEmitter(UUID retroId, HttpServletRequest request, String participantName, UUID participantId) {
        String connectionId = retroId + ":" + request.getSession().getId();
        String participantInfo = participantName + " (" + participantId + ")";
        
        // Check if connection already exists - close the old one first
        SseEmitter existingEmitter = localEmitters.get(connectionId);
        if (existingEmitter != null) {
            log.debug("Replacing existing SSE connection for participant {} in retro {}", participantInfo, retroId);
            cleanupConnection(connectionId, existingEmitter, participantInfo, retroId);
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        // Store locally
        localEmitters.put(connectionId, emitter);
        int currentConnections = activeConnections.incrementAndGet();

        // Cleanup on close/error/timeout - use a single cleanup method to avoid race conditions
        emitter.onCompletion(() -> {
            cleanupConnection(connectionId, emitter, participantInfo, retroId);
            log.trace("SSE connection completed for participant {} in retro {}", participantInfo, retroId);
        });

        emitter.onTimeout(() -> {
            cleanupConnection(connectionId, emitter, participantInfo, retroId);
            log.warn("SSE connection timed out for participant {} in retro {}", participantInfo, retroId);
        });

        emitter.onError((error) -> {
            // Check if this is a normal client disconnection (user navigating away)
            boolean isClientDisconnection = error instanceof org.springframework.web.context.request.async.AsyncRequestNotUsableException ||
                error.getMessage().contains("Broken pipe") ||
                error.getMessage().contains("Connection reset") ||
                error.getMessage().contains("disconnected client");

            if (isClientDisconnection) {
                log.trace("SSE connection closed by client for participant {} in retro {}", participantInfo, retroId);
            } else {
                log.warn("SSE connection error for participant {} in retro {}: {}", participantInfo, retroId, error.getMessage());
            }

            cleanupConnection(connectionId, emitter, participantInfo, retroId);
        });

        log.debug("SSE emitter created for participant {} in retro {} (total active: {})", participantInfo, retroId, currentConnections);

        // Send initial comment to establish the SSE connection
        // This prevents browsers from closing the connection immediately due to no initial data
        try {
            emitter.send(SseEmitter.event()
                .comment("SSE connection established"));
            log.trace("Sent initial SSE connection comment for participant {} in retro {}", participantInfo, retroId);
        } catch (IOException e) {
            log.warn("Failed to send initial SSE comment for participant {} in retro {}: {}", participantInfo, retroId, e.getMessage());
            cleanupConnection(connectionId, emitter, participantInfo, retroId);
            throw new RuntimeException("Failed to establish SSE connection", e);
        }

        return emitter;
    }
    
    /**
     * Thread-safe cleanup method to avoid double-decrements.
     */
    private void cleanupConnection(String connectionId, SseEmitter emitter, String participantInfo, UUID retroId) {
        // Only cleanup if the emitter in the map matches the one we're cleaning up
        // This prevents race conditions where multiple cleanup calls happen for the same connection
        if (localEmitters.remove(connectionId, emitter)) {
            activeConnections.decrementAndGet();
            log.trace("Cleaned up SSE connection for participant {} in retro {} (active: {})", participantInfo, retroId, activeConnections.get());
            try {
                emitter.complete();
            } catch (Exception e) {
                // Silently ignore completion errors during cleanup - these are expected when client disconnects
                log.trace("Error completing emitter during cleanup for participant {} in retro {} (expected): {}", participantInfo, retroId, e.getMessage());
            }
        }
    }
    
    /**
     * Send to local emitters.
     */
    private void sendToLocalEmitters(UUID retroId, RetroEvent<?> event) {
        String connectionId = retroId + ":";

        // Take a snapshot of matching emitters to avoid concurrent modification
        List<Map.Entry<String, SseEmitter>> matchingEmitters = localEmitters.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(connectionId))
            .toList();

        log.debug("Sending {} event to {} connection(s) for retro {}",
            event.type(), matchingEmitters.size(), retroId);

        matchingEmitters.forEach(entry -> {
            try {
                // Send properly formatted SSE event using SseEmitter.event() builder
                // Use the event type as the SSE event name, and pass the actual payload as data
                String eventData = event.payload() != null ? event.payload().toString() : "refresh";
                entry.getValue().send(SseEmitter.event()
                    .name(event.type().name().toLowerCase())
                    .data(eventData));

                log.trace("Sent {} event to connection: {}", event.type(), entry.getKey());
            } catch (IOException e) {
                log.warn("Failed to send {} event to connection: {} - {}", event.type(), entry.getKey(), e.getMessage());
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
