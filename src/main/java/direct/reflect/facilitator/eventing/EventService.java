package direct.reflect.facilitator.eventing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import direct.reflect.facilitator.facilitation.RetroSyncVersionService;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

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
import java.util.stream.Collectors;

@Service
public class EventService {
    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final RetroSyncVersionService retroSyncVersionService;
    private final AtomicInteger activeConnections = new AtomicInteger(0);

    public EventService(
            RedisTemplate<String, Object> redisTemplate,
            ObjectMapper objectMapper,
            ApplicationEventPublisher applicationEventPublisher,
            RetroSyncVersionService retroSyncVersionService) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.applicationEventPublisher = applicationEventPublisher;
        this.retroSyncVersionService = retroSyncVersionService;
    }

    @Value("${facilitator.sse.timeout-ms:3600000}")
    private long sseTimeoutMs;

    // Record to store SSE emitter with participant info
    private record EmitterConnection(SseEmitter emitter, String participantName) {}

    // Local SSE connections for this pod instance
    private final ConcurrentHashMap<String, EmitterConnection> localEmitters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService keepAliveExecutor = Executors.newScheduledThreadPool(2,
        r -> {
            Thread t = new Thread(r, "sse-keepalive-" + System.nanoTime());
            t.setDaemon(true);
            return t;
        });

    @PostConstruct
    public void init() {
        log.info("EventService initializing with Redis Pub/Sub");

        // Start keep-alive scheduler for SSE connections
        keepAliveExecutor.scheduleAtFixedRate(this::sendKeepAlive, 30, 30, TimeUnit.SECONDS);

        log.info("EventService initialized - SSE keep-alive scheduled every 30s");
    }
    
    @PreDestroy
    public void cleanup() {
        // Close all local SSE connections
        localEmitters.values().forEach(connection -> {
            try {
                connection.emitter().complete();
            } catch (Exception e) {
                log.warn("Error closing SSE emitter: {}", e.getMessage());
            }
        });
        keepAliveExecutor.shutdown();
    }
    
    /**
     * Publishes RetroEvent with transaction awareness.
     *
     * When called within @Transactional methods, events are automatically
     * delayed until AFTER transaction commits via @TransactionalEventListener.
     * This prevents race conditions where SSE clients query stale database state.
     *
     * For direct Redis publishing without transaction awareness (rare),
     * use broadcastToRedis() directly.
     */
    public void publish(RetroEvent<?> event) {
        log.debug("[{}] Publishing {} event for retro {} (will be broadcast after commit if in transaction)",
            event.correlationId(), event.type(), event.retroId());

        // Delegate to Spring's event system - will be queued if in transaction
        applicationEventPublisher.publishEvent(event);
    }

    /**
     * Handles events received from Redis Pub/Sub.
     * Called by MessageListenerAdapter for ALL events published by ANY pod (broadcast).
     * Forwards events to local SSE connections for this retro.
     */
    public void onPubSubMessage(Object message, String channel) {
        try {
            // Deserialize JSON string message to RetroEvent
            String jsonMessage = message.toString();
            RetroEvent<?> event = objectMapper.readValue(jsonMessage, RetroEvent.class);
            UUID retroId = event.retroId();

            log.debug("[{}] Received from Redis Pub/Sub: {} event for retro {} on channel {}",
                event.correlationId(), event.type(), retroId, channel);

            // Forward to local SSE connections only
            sendToLocalEmitters(retroId, event);

        } catch (Exception e) {
            log.error("Failed to process Pub/Sub message from channel {}: {}",
                channel, e.getMessage(), e);
        }
    }

    /**
     * Create SSE emitter (simple blocking approach).
     * Ensures only one connection per user per retro.
     * Uses participantId for connection keying to prevent stale connections.
     */
    public SseEmitter createSseEmitter(UUID retroId, UUID participantId, String participantName) {
        String connectionId = retroId + ":" + participantId;
        String participantInfo = participantName + " (" + participantId + ")";

        // Check if connection already exists - close the old one first
        EmitterConnection existingConnection = localEmitters.get(connectionId);
        if (existingConnection != null) {
            log.debug("[SSE] Replacing existing connection for {} in retro {}",
                participantInfo, retroId);
            cleanupConnection(connectionId, existingConnection.emitter(), participantInfo, retroId);
        }

        SseEmitter emitter = new SseEmitter(sseTimeoutMs);

        // Store locally with participant name
        localEmitters.put(connectionId, new EmitterConnection(emitter, participantName));
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

        log.debug("[SSE] Connection established: {} in retro {} (total active: {})",
            participantInfo, retroId, currentConnections);

        // Send initial comment to establish the SSE connection
        // This prevents browsers from closing the connection immediately due to no initial data
        try {
            emitter.send(SseEmitter.event()
                .comment("SSE connection established"));
            log.trace("[SSE] Sent initial connection comment to {}", participantInfo);
        } catch (IOException e) {
            log.error("[SSE] Failed to establish connection for {}: {}",
                participantInfo, e.getMessage());
            cleanupConnection(connectionId, emitter, participantInfo, retroId);
            throw new RuntimeException("Failed to establish SSE connection", e);
        }

        return emitter;
    }
    
    /**
     * Thread-safe cleanup method to avoid double-decrements.
     */
    private void cleanupConnection(String connectionId, SseEmitter emitter, String participantInfo, UUID retroId) {
        EmitterConnection existingConnection = localEmitters.get(connectionId);

        // Only cleanup if the stored connection still points at this emitter.
        // localEmitters stores EmitterConnection wrappers, so remove using the wrapper value.
        if (existingConnection != null
                && existingConnection.emitter() == emitter
                && localEmitters.remove(connectionId, existingConnection)) {
            int remainingConnections = activeConnections.decrementAndGet();
            log.trace("Cleaned up SSE connection for participant {} in retro {} (active: {})", participantInfo, retroId, remainingConnections);
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
        List<Map.Entry<String, EmitterConnection>> matchingEmitters = localEmitters.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(connectionId))
            .toList();

        // Build recipient list for logging
        String recipients = matchingEmitters.stream()
            .map(entry -> entry.getValue().participantName())
            .collect(Collectors.joining(", "));

        log.debug("[{}] Broadcasting {} to {} recipient(s): [{}]",
            event.correlationId(), event.type(), matchingEmitters.size(), recipients);

        String eventData;
        try {
            eventData = objectMapper.writeValueAsString(toSseEnvelope(event));
        } catch (JacksonException e) {
            log.error("[{}] Failed to serialize payload for event type {}: {}",
                event.correlationId(), event.type(), e.getMessage());
            return;
        }

        int successCount = 0;
        int failureCount = 0;

        for (Map.Entry<String, EmitterConnection> entry : matchingEmitters) {
            String participantName = entry.getValue().participantName();
            try {
                entry.getValue().emitter().send(SseEmitter.event()
                    .id(event.correlationId())
                    .name(event.type().name().toLowerCase())
                    .data(eventData));

                log.trace("[{}] Delivered to {}", event.correlationId(), participantName);
                successCount++;

            } catch (Exception e) {
                log.warn("[{}] Failed to deliver to {}: {}",
                    event.correlationId(), participantName, e.getMessage());
                failureCount++;
                // Don't manually cleanup here - let the emitter's error handler deal with it
                // This prevents double-cleanup and negative connection counts
            }
        }

        log.debug("[{}] Broadcast completed: {} succeeded, {} failed",
            event.correlationId(), successCount, failureCount);
    }

    RetroSseEnvelope<?> toSseEnvelope(RetroEvent<?> event) {
        long syncVersion = retroSyncVersionService.getSyncVersion(event.retroId());
        return new RetroSseEnvelope<>(syncVersion, event.payload());
    }
    
    /**
     * Send keep-alive messages to all active connections.
     */
    private void sendKeepAlive() {
        if (localEmitters.isEmpty()) {
            return;
        }

        // Take a snapshot to avoid concurrent modification
        List<Map.Entry<String, EmitterConnection>> currentEmitters = new ArrayList<>(localEmitters.entrySet());

        currentEmitters.forEach(entry -> {
            try {
                entry.getValue().emitter().send(SseEmitter.event()
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

    /**
     * Publishes RetroEvent to Redis Pub/Sub AFTER PostgreSQL transaction commits.
     *
     * This listener is triggered automatically when services call eventService.publish()
     * within @Transactional methods. The @TransactionalEventListener annotation ensures
     * events are only broadcast to SSE clients AFTER the transaction commits, preventing
     * race conditions where clients query stale database state.
     *
     * Pattern:
     * 1. Service calls: eventService.publish(retroEvent)
     * 2. EventService.publish() delegates to ApplicationEventPublisher
     * 3. Spring queues event until transaction commits
     * 4. Transaction commits (database changes now visible)
     * 5. This listener receives event (@TransactionalEventListener AFTER_COMMIT)
     * 6. Calls broadcastToRedis() → Redis Pub/Sub → SSE clients
     *
     * Multi-Instance (K8s) Behavior:
     * - ApplicationEvent is JVM-local (not distributed across pods)
     * - Each pod's listener publishes to Redis after ITS transaction commits
     * - Redis broadcasts to ALL pods' SSE clients
     * - Database consistency guaranteed: clients query AFTER commit
     *
     * Usage example:
     * <pre>
     * {@code
     * @Transactional
     * public void removeParticipantFromSession(Participant participant) {
     *     participantRepository.save(participant);  // DB write
     *
     *     // Publish event - automatically delayed until transaction commits
     *     eventService.publish(
     *         RetroEvent.participantLeft(sessionId, displayName)
     *     );
     * }  // Transaction commits → this listener fires → Redis publishes
     * }
     * </pre>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRetroEventAfterCommit(RetroEvent<?> event) {
        log.debug("[{}] Transaction committed - broadcasting {} event to Redis (retroId: {})",
            event.correlationId(), event.type(), event.retroId());

        try {
            broadcastToRedis(event);  // Call internal broadcasting method
            log.info("[{}] Broadcast {} event to Redis for retro {} (participants notified via SSE)",
                event.correlationId(), event.type(), event.retroId());
        } catch (Exception e) {
            log.error("[{}] Failed to broadcast {} event to Redis: {}",
                event.correlationId(), event.type(), e.getMessage(), e);
            // Don't throw - SSE event failure shouldn't crash the application
            // Database changes are already committed and visible to queries
        }
    }

    /**
     * Internal method that broadcasts directly to Redis Pub/Sub.
     *
     * This method contains the actual Redis broadcasting logic. It's kept private
     * for rare cases where non-transactional broadcasting is needed.
     *
     * Most code should call publish() instead, which provides transaction awareness.
     */
    private void broadcastToRedis(RetroEvent<?> event) {
        String channel = "retro:" + event.retroId();

        // Broadcast to Redis Pub/Sub (all pods will receive)
        // RedisTemplate handles JSON serialization automatically
        redisTemplate.convertAndSend(channel, event);

        log.debug("[{}] Broadcast to Redis channel: {}",
            event.correlationId(), channel);
    }

}
