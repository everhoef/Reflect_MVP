package direct.reflect.facilitator.facilitation;

import org.springframework.stereotype.Service;
import direct.reflect.facilitator.configurator.RetroStage;
import direct.reflect.facilitator.configurator.RetroStep;
import direct.reflect.facilitator.configurator.RetroTemplate;
import direct.reflect.facilitator.common.exception.RetroSessionNotFoundException;
import direct.reflect.facilitator.common.exception.RetroTemplateNotFoundException;
import direct.reflect.facilitator.configurator.RetroStepRepository;
import direct.reflect.facilitator.configurator.RetroTemplateRepository;
import direct.reflect.facilitator.eventing.EventService;
import direct.reflect.facilitator.eventing.EventType;
import direct.reflect.facilitator.eventing.RetroEvent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.transaction.Transactional;
import org.springframework.context.annotation.Lazy;

@Service
@Slf4j
@RequiredArgsConstructor
public class RetroSessionService {
    private final RetroSessionRepository sessionRepository;
    private final RetroTemplateRepository templateRepository;
    private final RetroStepRepository stepRepository;
    private final @Lazy EventService eventService;

    @Transactional
    public RetroSession createNewSession(String sessionName, RetroTemplate template) {
        RetroSession session = new RetroSession();
        session.setName(sessionName);
        session.setCreatedAt(LocalDateTime.now());
        session.setTemplate(template);
        session.setPhase(RetroPhase.LOBBY);
        
        RetroSession savedSession = sessionRepository.save(session);
        
        // Publish RETRO_CREATED event to establish SSE connection immediately
        eventService.publish(RetroEvent.retroCreated(savedSession.getId(), "system"));
        log.info("Published RETRO_CREATED event for session: {}", savedSession.getName());
        
        return savedSession;
    }

    @Transactional
    public void startSession(UUID sessionId) {
        RetroSession session = getSessionOrThrow(sessionId);
        session.setPhase(RetroPhase.SET_THE_STAGE);
        sessionRepository.save(session);
        
        // Start first step
        advanceToNextStep(sessionId);
        // TODO: Notify participants
    }

    public RetroTemplate getDefaultTemplate() {
        // Assuming there's a default template defined in the system
        return templateRepository.findById(1L)
            .orElseThrow(() -> new RetroTemplateNotFoundException(1L));
    }

    @Transactional
    public void advanceToNextStep(UUID sessionId) {
        RetroSession session = getSessionOrThrow(sessionId);
        RetroStage currentStage = session.getCurrentStage();
        
        // Ensure template and stage are loaded if lazy
        if (session.getTemplate() == null || currentStage == null) {
            log.warn("Session {} or its current stage is not properly initialized (template or stage is null). Cannot advance step.", sessionId);
            // Potentially throw an exception or handle gracefully
            return; 
        }
        List<RetroStep> stageSteps = stepRepository.findByStageId(currentStage.getId());
        
        if (session.getCurrentStepIndex() + 1 < stageSteps.size()) {
            // More steps in current stage
            session.setCurrentStepIndex(session.getCurrentStepIndex() + 1);
        } else {
            // No more steps, advance to next phase
            RetroPhase currentPhase = session.getPhase();
            if (currentPhase == null) {
                 log.error("Session {} has a null current phase. Cannot advance.", sessionId);
                 // Or throw an exception
                 return;
            }
            RetroPhase nextPhase = currentPhase.next();
            session.setPhase(nextPhase);
            session.setCurrentStepIndex(-1); // Reset step index for the new phase
            
            if (nextPhase == RetroPhase.COMPLETED) {
                session.setFinishedAt(LocalDateTime.now());
            }
        }

        sessionRepository.save(session);
    }

    @Transactional
    public RetroStep getCurrentStep(UUID sessionId) {
        RetroSession session = getSessionOrThrow(sessionId);
        RetroStage currentStage = session.getCurrentStage();
        if (currentStage == null || session.getCurrentStepIndex() < 0) {
            return null;
        }
        
        List<RetroStep> steps = stepRepository.findByStageId(currentStage.getId());
        if (steps.isEmpty() || session.getCurrentStepIndex() >= steps.size()) {
            return null; // No steps in stage or index out of bounds
        }
        return steps.get(session.getCurrentStepIndex());
    }

    public boolean sessionExists(UUID sessionId) {
        return sessionRepository.findById(sessionId).isPresent();
    }

    public String getSessionName(UUID sessionId) {
        return getSessionOrThrow(sessionId).getName();
    }

    public RetroSession getSessionById(UUID sessionId) {
        return getSessionOrThrow(sessionId);
    }

    private RetroSession getSessionOrThrow(UUID sessionId) {
        return sessionRepository.findById(sessionId)
            .orElseThrow(() -> new RetroSessionNotFoundException(sessionId));
    }

    public List<RetroTemplate> getAvailableTemplates() {
        return templateRepository.findAll();
    }

    public RetroTemplate getTemplateById(Long templateId) {
        return templateRepository.findById(templateId)
            .orElseThrow(() -> new RetroTemplateNotFoundException(templateId));
    }

    /**
     * Called when a participant is leaving a session to publish appropriate events.
     */
    public void onParticipantLeaving(Participant participant) {
        log.debug("Publishing PARTICIPANT_LEFT event for '{}' from session {}", 
            participant.getDisplayName(), participant.getSession().getId());
        
        try {
            eventService.publish(RetroEvent.participantLeft(
                participant.getSession().getId(), 
                participant.getDisplayName()
            ));
            log.info("Published PARTICIPANT_LEFT event for '{}'", participant.getDisplayName());
        } catch (Exception eventError) {
            log.error("Failed to publish PARTICIPANT_LEFT event: {}", eventError.getMessage());
            // Continue anyway - event publishing failure shouldn't block user flow
        }
    }
}
