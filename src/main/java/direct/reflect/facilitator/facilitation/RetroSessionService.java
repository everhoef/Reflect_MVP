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
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Lazy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

@Service
@Slf4j
@RequiredArgsConstructor
public class RetroSessionService {
    private final RetroSessionRepository sessionRepository;
    private final RetroTemplateRepository templateRepository;
    private final RetroStepRepository stepRepository;
    private final @Lazy EventService eventService;
    private final ObjectMapper objectMapper;

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
        // Note: Participant notification is handled at the API layer via eventService.publish()
    }

    public RetroTemplate getDefaultTemplate() {
        // Assuming there's a default template defined in the system
        return templateRepository.findById(1L)
            .orElseThrow(() -> new RetroTemplateNotFoundException(1L));
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
    
    /**
     * Get the current RetroStep for a session based on current phase and step index
     */
    public RetroStep getCurrentStep(UUID sessionId) {
        RetroSession session = getSessionById(sessionId);
        RetroStage currentStage = session.getCurrentStage();
        if (currentStage == null) {
            return null;
        }
        
        int stepIndex = session.getCurrentStepIndex();
        if (stepIndex < 0) {
            return null; // Session hasn't started steps yet
        }
        
        List<RetroStep> stepsForStage = stepRepository.findByRetroStageOrderByOrderIndexAsc(currentStage);
        if (stepIndex >= stepsForStage.size()) {
            return null; // Invalid step index
        }
        
        return stepsForStage.get(stepIndex);
    }
    
    /**
     * Check if there's a next step within the current stage
     */
    public boolean hasNextStepInCurrentStage(UUID sessionId) {
        RetroSession session = getSessionById(sessionId);
        RetroStage currentStage = session.getCurrentStage();
        if (currentStage == null) {
            return false;
        }
        
        List<RetroStep> stepsForStage = stepRepository.findByRetroStageOrderByOrderIndexAsc(currentStage);
        return (session.getCurrentStepIndex() + 1) < stepsForStage.size();
    }
    
    /**
     * Advance to the next step within the current stage, or to the next phase if stage is complete.
     * Future: This method can be extended to save metadata like completion times, participant counts, etc.
     */
    @Transactional
    public void advanceToNextStep(UUID sessionId) {
        RetroSession session = getSessionById(sessionId);
        RetroStage currentStage = session.getCurrentStage();
        
        // TODO: Future metadata tracking
        // - Record step/stage completion time
        // - Track participant engagement metrics  
        // - Save facilitator notes or decisions
        
        if (session.getCurrentStepIndex() < 0) {
            // Start the first step of the current stage
            session.setCurrentStepIndex(0);
            log.info("Started first step of stage '{}' in session {}", 
                currentStage != null ? currentStage.getName() : "unknown", sessionId);
        } else if (hasNextStepInCurrentStage(sessionId)) {
            // Advance to next step within current stage
            session.setCurrentStepIndex(session.getCurrentStepIndex() + 1);
            log.info("Advanced to step {} in stage '{}' for session {}", 
                session.getCurrentStepIndex(), 
                currentStage != null ? currentStage.getName() : "unknown", 
                sessionId);
        } else {
            // Current stage is complete, advance to next phase
            RetroPhase previousPhase = session.getPhase();
            session.advancePhase();
            session.setCurrentStepIndex(0); // Start first step of new stage
            
            // Handle session completion
            if (session.getPhase() == RetroPhase.COMPLETED) {
                session.setFinishedAt(LocalDateTime.now());
            }
            
            log.info("Advanced session {} from phase {} to {} and started step 0", 
                sessionId, previousPhase, session.getPhase());
        }
        
        sessionRepository.save(session);
    }

    public RetroTemplate getTemplateById(Long templateId) {
        return templateRepository.findById(templateId)
            .orElseThrow(() -> new RetroTemplateNotFoundException(templateId));
    }

    /**
     * Called when a participant is joining a session to publish appropriate events.
     */
    public void onParticipantJoining(Participant participant) {
        log.debug("Publishing PARTICIPANT_JOINED event for '{}' joining session {}", 
            participant.getDisplayName(), participant.getSession().getId());
        
        try {
            eventService.publish(RetroEvent.participantJoined(
                participant.getSession().getId(), 
                participant.getDisplayName()
            ));
            log.info("Published PARTICIPANT_JOINED event for '{}'", participant.getDisplayName());
        } catch (Exception eventError) {
            log.error("Failed to publish PARTICIPANT_JOINED event: {}", eventError.getMessage());
            // Continue anyway - event publishing failure shouldn't block user flow
        }
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

    /**
     * Extract guidance content from step configuration JSON.
     * Returns specific step guidance if available, otherwise generic guidance based on step type.
     */
    public String getStepGuidanceContent(RetroStep step) {
        if (step == null) {
            return "Follow the instructions in the main area.";
        }

        try {
            // Try to parse the configuration JSON and extract content
            JsonNode configNode = objectMapper.readTree(step.getConfiguration());
            if (configNode.has("content")) {
                return configNode.get("content").asText();
            }
        } catch (Exception e) {
            log.debug("Could not parse step configuration as JSON for step {}, using fallback guidance", step.getId());
        }

        // Fallback to generic guidance based on step type
        return switch (step.getStepType()) {
            case INSTRUCTION -> "Read and understand what we'll do next. This sets up the upcoming activity.";
            case ACTIVITY -> "Take action by sharing your thoughts and participating in this step.";
            case DISCUSSION -> "Look at the results and share observations. What patterns do you notice?";
            default -> "Follow the instructions in the main area.";
        };
    }
}
