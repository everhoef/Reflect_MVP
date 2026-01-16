package direct.reflect.facilitator.facilitation;

import org.springframework.stereotype.Service;
import direct.reflect.facilitator.configurator.RetroStage;
import direct.reflect.facilitator.configurator.RetroStep;
import direct.reflect.facilitator.configurator.RetroTemplate;
import direct.reflect.facilitator.configurator.RetroTemplateService;
import direct.reflect.facilitator.configurator.AdvancementTrigger;
import direct.reflect.facilitator.configurator.ComponentType;
import direct.reflect.facilitator.common.exception.RetroSessionNotFoundException;
import direct.reflect.facilitator.configurator.RetroStepRepository;
import direct.reflect.facilitator.facilitation.response.ParticipantResponseRepository;
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
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

@Service
@Slf4j
@RequiredArgsConstructor
public class RetroSessionService {
    private final RetroSessionRepository sessionRepository;
    private final RetroTemplateService retroTemplateService;
    private final RetroStepRepository stepRepository;
    private final ParticipantResponseRepository responseRepository;
    private final ParticipantService participantService;
    private final EventService eventService;
    private final ObjectMapper objectMapper;

    @Transactional
    public RetroSession createNewSession(String sessionName) {
        // Select appropriate template for this session
        RetroTemplate template = retroTemplateService.selectTemplateForSession();

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

    /**
     * Creates a new retro session with the current user as facilitator.
     * Orchestrates the complete workflow: leave active sessions, create session, assign facilitator.
     */
    @Transactional
    public RetroSession createSessionWithFacilitator(String sessionName, HttpServletRequest request) {
        // Step 1: Leave any active sessions for this user
        participantService.leaveAllActiveSessions(request);
        log.debug("Terminated active sessions before creating new session");

        // Step 2: Create the retro session (publishes RETRO_CREATED event)
        RetroSession session = createNewSession(sessionName);
        log.info("Created new retro session: {} (id: {})", session.getName(), session.getId());

        // Step 3: Add creator as facilitator (publishes PARTICIPANT_JOINED event)
        participantService.addParticipantToSession(request, session, ParticipantRole.FACILITATOR);
        log.info("Assigned facilitator to session {}", session.getId());

        return session;
    }

    @Transactional
    public void startSession(UUID sessionId) {
        RetroSession session = getSessionOrThrow(sessionId);
        session.setPhase(RetroPhase.SET_THE_STAGE);
        sessionRepository.save(session);

        // Start first step
        advanceToNextStep(sessionId);

        // Publish session_started event (must be within transaction for @TransactionalEventListener)
        eventService.publish(RetroEvent.sessionStarted(sessionId));
        log.info("Published SESSION_STARTED event for session {}", sessionId);
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
        return retroTemplateService.getAvailableTemplates();
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
     * Find a step with the specified componentType in the given stage
     */
    public RetroStep findStepByComponentType(RetroStage stage, ComponentType componentType) {
        List<RetroStep> steps = stepRepository.findByRetroStageAndComponentType(stage, componentType);
        return steps.isEmpty() ? null : steps.get(0); // Return first match
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
     * Check if the current step can advance based on its AdvancementTrigger.
     * Does not consider facilitator override.
     */
    public boolean canAdvanceCurrentStep(UUID sessionId) {
        RetroStep currentStep = getCurrentStep(sessionId);
        if (currentStep == null) {
            return false;
        }

        return switch (currentStep.getAdvancementTrigger()) {
            case FACILITATOR_CLICK -> true;
            case ALL_RESPONDED -> allParticipantsResponded(sessionId, currentStep.getId());
            case TIMER_EXPIRES -> timerHasExpired(sessionId, currentStep);
            case AUTO -> true;
        };
    }

    private boolean allParticipantsResponded(UUID sessionId, Long stepId) {
        RetroSession session = getSessionById(sessionId);
        RetroStep step = stepRepository.findById(stepId).orElse(null);
        if (step == null) {
            return false;
        }

        long totalParticipants = participantService.getSessionParticipants(sessionId).size();
        long respondedParticipants = responseRepository.countDistinctParticipantsBySessionAndStep(session, step);

        return respondedParticipants >= totalParticipants;
    }

    private boolean timerHasExpired(UUID sessionId, RetroStep step) {
        if (step.getDurationSeconds() == null || step.getDurationSeconds() <= 0) {
            return true;
        }

        RetroSession session = getSessionById(sessionId);
        if (session.getStepStartedAt() == null) {
            return false;
        }

        LocalDateTime expirationTime = session.getStepStartedAt().plusSeconds(step.getDurationSeconds());
        return LocalDateTime.now().isAfter(expirationTime);
    }

    /**
     * Get instruction messages for chatbox display.
     * Returns all instructions from steps in the current stage up to current step.
     */
    public List<String> getInstructionHistory(UUID sessionId) {
        RetroSession session = getSessionById(sessionId);
        RetroStage currentStage = session.getCurrentStage();

        if (currentStage == null || session.getCurrentStepIndex() < 0) {
            return List.of();
        }

        List<RetroStep> stepsInStage = stepRepository.findByRetroStageOrderByOrderIndexAsc(currentStage);

        return stepsInStage.stream()
            .limit(session.getCurrentStepIndex() + 1)
            .map(RetroStep::getInstructions)
            .filter(instr -> instr != null && !instr.isBlank())
            .toList();
    }

    /**
     * Advance to the next step within the current stage, or to the next phase if stage is complete.
     * Future: This method can be extended to save metadata like completion times, participant counts, etc.
     */
    @Transactional
    public void advanceToNextStep(UUID sessionId) {
        RetroSession session = getSessionById(sessionId);
        RetroStage currentStage = session.getCurrentStage();

        if (session.getCurrentStepIndex() < 0) {
            session.setCurrentStepIndex(0);
            session.setStepStartedAt(LocalDateTime.now());
            log.info("Started first step of stage '{}' in session {}",
                currentStage != null ? currentStage.getName() : "unknown", sessionId);
        } else if (hasNextStepInCurrentStage(sessionId)) {
            session.setCurrentStepIndex(session.getCurrentStepIndex() + 1);
            session.setStepStartedAt(LocalDateTime.now());
            log.info("Advanced to step {} in stage '{}' for session {}",
                session.getCurrentStepIndex(),
                currentStage != null ? currentStage.getName() : "unknown",
                sessionId);
        } else {
            RetroPhase previousPhase = session.getPhase();
            session.advancePhase();
            session.setCurrentStepIndex(0);
            session.setStepStartedAt(LocalDateTime.now());

            if (session.getPhase() == RetroPhase.COMPLETED) {
                session.setFinishedAt(LocalDateTime.now());
            }

            log.info("Advanced session {} from phase {} to {} and started step 0",
                sessionId, previousPhase, session.getPhase());
        }

        sessionRepository.save(session);

        // Publish step_advanced event (must be within transaction for @TransactionalEventListener)
        eventService.publish(RetroEvent.stepAdvanced(sessionId));
        log.info("Published STEP_ADVANCED event for session {}", sessionId);
    }
}
