package direct.reflect.facilitator.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import direct.reflect.facilitator.dto.RetrospectiveSessionResponseDTO;
import direct.reflect.facilitator.dto.EventType;
import direct.reflect.facilitator.dto.RetrospectiveSessionJoinDTO;
import direct.reflect.facilitator.entity.RetrospectiveSession;
import direct.reflect.facilitator.exception.RetrospectiveSessionNotFoundException;
import direct.reflect.facilitator.mapper.RetrospectiveSessionMapper;
import direct.reflect.facilitator.repository.RetrospectiveSessionRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.Map;
import org.springframework.security.core.context.SecurityContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class RetrospectiveService {

  private static final Logger log = LoggerFactory.getLogger(RetrospectiveService.class);

  @Autowired
  private RetrospectiveSessionRepository sessionRepository;

  @Autowired
  private RetrospectiveSessionMapper mapper;

  @Autowired
  private RetrospectiveEventService eventService;

  public RetrospectiveSessionResponseDTO createSession(String facilitator) {
    RetrospectiveSession session = new RetrospectiveSession();
    session.setRetroId(UUID.randomUUID());
    session.setCreatedAt(LocalDateTime.now());
    session.setFacilitator(facilitator);
    session.addParticipant(facilitator); // Uses new addParticipant method
    sessionRepository.save(session);
    return mapper.toResponseDTO(session);
  }

  public RetrospectiveSessionResponseDTO joinSession(RetrospectiveSessionJoinDTO joinDTO) {
    RetrospectiveSession session = sessionRepository.findByRetroId(joinDTO.getId())
        .orElseThrow(() -> new RetrospectiveSessionNotFoundException(joinDTO.getId()));

    if (session.findParticipant(joinDTO.getUsername()).isEmpty()) {
      session.addParticipant(joinDTO.getUsername());
      sessionRepository.save(session);

      // Simply emit the event, no data needed
      eventService.emit(joinDTO.getId(), EventType.PARTICIPANT_JOINED, null);
    }

    return mapper.toResponseDTO(session);
  }

  public RetrospectiveSessionResponseDTO getSession(UUID retroId) {
    RetrospectiveSession session = sessionRepository.findByRetroId(retroId)
        .orElseThrow(() -> new RetrospectiveSessionNotFoundException(retroId));
    return mapper.toResponseDTO(session);
  }

  public List<String> getParticipants(UUID retroId) {
    RetrospectiveSession session = sessionRepository.findByRetroId(retroId)
        .orElseThrow(() -> new RetrospectiveSessionNotFoundException(retroId));
    return session.getParticipantUsernames(); // Uses new method name
  }

  public RetrospectiveSessionResponseDTO startSession(UUID sessionId) {
    RetrospectiveSession session = sessionRepository.findByRetroId(sessionId)
        .orElseThrow(() -> new RetrospectiveSessionNotFoundException(sessionId));

    // Simply emit the event
    eventService.emit(sessionId, EventType.SESSION_STARTED, null);

    return mapper.toResponseDTO(session);
  }

  public SseEmitter subscribe(UUID retroId, String username, String lastEventId) {
    log.info("Subscribe request from user {} for session {}", username, retroId);

    RetrospectiveSession session = sessionRepository.findByRetroId(retroId)
        .orElseThrow(() -> new RetrospectiveSessionNotFoundException(retroId));

    if (session.findParticipant(username).isEmpty()) {
      log.error("User {} attempted to subscribe but is not a participant in session {}", username,
          retroId);
      throw new IllegalStateException("User is not a participant in this session");
    }

    return eventService.subscribe(retroId, username, lastEventId);
  }
}
