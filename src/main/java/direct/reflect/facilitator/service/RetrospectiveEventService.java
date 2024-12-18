package direct.reflect.facilitator.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import direct.reflect.facilitator.dto.*;
import direct.reflect.facilitator.entity.Participant;
import direct.reflect.facilitator.entity.RetrospectiveSession;
import direct.reflect.facilitator.exception.RetrospectiveSessionNotFoundException;
import direct.reflect.facilitator.repository.RetrospectiveSessionRepository;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
class RetrospectiveEventService {
  private static final Logger log = LoggerFactory.getLogger(RetrospectiveEventService.class);
  private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
  private final RetrospectiveSessionRepository sessionRepository;

  public RetrospectiveEventService(RetrospectiveSessionRepository sessionRepository) {
    this.sessionRepository = sessionRepository;
  }

  public SseEmitter subscribe(UUID sessionId, String username, String lastEventId) {
    try {
      log.info("Subscribing user {} to session {}", username, sessionId);

      RetrospectiveSession session = sessionRepository.findByRetroId(sessionId)
          .orElseThrow(() -> new RetrospectiveSessionNotFoundException(sessionId));

      String clientId = UUID.randomUUID().toString();
      SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

      // Clean up existing connection if any
      session.findParticipant(username).ifPresent(participant -> {
        if (participant.getSseClientId() != null) {
          Optional.ofNullable(emitters.remove(participant.getSseClientId()))
              .ifPresent(SseEmitter::complete);
        }
      });

      // Clean up any existing emitters for this user in this session
      emitters.entrySet().removeIf(entry -> {
        if (entry.getKey().startsWith(username + "-")) {
          log.debug("Removing stale connection for user: {}", username);
          entry.getValue().complete();
          return true;
        }
        return false;
      });

      // Update participant connection status
      session.findParticipant(username).ifPresent(participant -> {
        participant.setSseClientId(clientId);
        participant.setLastSeen(LocalDateTime.now());
        participant.setConnected(true);
        sessionRepository.save(session);
      });

      // Register new emitter
      emitters.put(clientId, emitter);

      // Setup completion and timeout handlers
      emitter.onCompletion(() -> handleDisconnect(sessionId, username, clientId));
      emitter.onTimeout(() -> handleDisconnect(sessionId, username, clientId));

      // Send initial connection event
      emitter.send(SseEmitter.event().id(clientId).name("INIT").data("Connected"));

      log.debug("New emitter registered. Total active emitters: {}", emitters.size());
      log.debug("SSE connection established for user {} in session {}", username, sessionId);
      return emitter;

    } catch (Exception e) {
      log.error("Error in subscribe method", e);
      throw new RuntimeException("Failed to set up SSE subscription", e);
    }
  }

  private void handleDisconnect(UUID sessionId, String username, String clientId) {
    log.debug("Handling disconnect for user {} in session {}", username, sessionId);
    emitters.remove(clientId);
    disconnectParticipant(sessionId, username);
  }

  private void sendEvent(SseEmitter emitter, EventType type, Object data) {
    try {
      SseEmitter.SseEventBuilder event =
          SseEmitter.event().name(type.toString()).data(data != null ? data : "");
      emitter.send(event);
    } catch (Exception e) {
      throw new RuntimeException("Failed to send SSE event", e);
    }
  }

  public void emit(UUID sessionId, EventType type, Object data) {
    RetrospectiveSession session = sessionRepository.findByRetroId(sessionId)
        .orElseThrow(() -> new RetrospectiveSessionNotFoundException(sessionId));

    Set<String> clientIds = session.getConnectedParticipants().stream()
        .map(Participant::getSseClientId).filter(Objects::nonNull).collect(Collectors.toSet());

    clientIds.forEach(clientId -> {
      SseEmitter emitter = emitters.get(clientId);
      if (emitter != null) {
        try {
          sendEvent(emitter, type, data);
        } catch (Exception e) {
          emitters.remove(clientId);
          disconnectParticipant(sessionId, clientId);
        }
      }
    });
  }

  private void disconnectParticipant(UUID sessionId, String username) {
    sessionRepository.findByRetroId(sessionId).ifPresent(session -> {
      session.findParticipant(username).ifPresent(participant -> {
        participant.setSseClientId(null);
        participant.setConnected(false);
        participant.setLastSeen(LocalDateTime.now());
        sessionRepository.save(session);
      });
    });
  }
}
