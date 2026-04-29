package direct.reflect.facilitator.facilitation.response;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import direct.reflect.facilitator.facilitation.session.RetroSession;
import direct.reflect.facilitator.facilitation.participant.Participant;
import direct.reflect.facilitator.facilitation.participant.ParticipantService;
import direct.reflect.facilitator.facilitation.session.RetroSessionService;
import direct.reflect.facilitator.facilitation.session.RetroSyncVersionService;
import direct.reflect.facilitator.facilitation.participant.ParticipantNotFoundException;
import direct.reflect.facilitator.facilitation.session.InvalidSessionStateException;
import direct.reflect.facilitator.facilitation.session.InvalidStepException;
import direct.reflect.facilitator.facilitation.session.RetroSessionNotFoundException;
import direct.reflect.facilitator.facilitation.dto.ComponentResponseDto;
import direct.reflect.facilitator.configurator.RetroStep;
import direct.reflect.facilitator.configurator.RetroStage;
import direct.reflect.facilitator.configurator.ComponentType;
import direct.reflect.facilitator.configurator.RetroStepQueryService;
import direct.reflect.facilitator.eventing.EventService;
import direct.reflect.facilitator.eventing.RetroEvent;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ResponseService {
    private final ParticipantResponseRepository responseRepository;
    private final RetroStepQueryService retroStepQueryService;
    private final EventService eventService;
    private final RetroSessionService retroSessionService;
    private final ParticipantService participantService;
    private final RetroSyncVersionService retroSyncVersionService;

    /**
     * Polymorphic method accepting ComponentResponseDto.
     * DTOs are validated at controller level with @Valid.
     * This method handles session/participant/step validation and delegates to internal method.
     */
    public ParticipantResponse submitResponse(
            UUID retroId,
            Long stepId,
            ComponentResponseDto dto,
            HttpServletRequest request) {

        // Validate participant access (throws ParticipantNotFoundException if unauthorized)
        // Security: This checks authorization BEFORE revealing session existence.
        // If unauthorized, throws generic error preventing session ID enumeration
        Participant participant = participantService.getParticipantForSession(request, retroId);

        // Get session from participant (we know they have access now)
        RetroSession session = participant.getSession();

        // Validate session is in active phase
        if (!session.getPhase().isActivePhase()) {
            throw new InvalidSessionStateException(session.getPhase());
        }

        // Validate step matches current step
        RetroStep currentStep = retroSessionService.getCurrentStep(retroId);
        if (!currentStep.getId().equals(stepId)) {
            throw new InvalidStepException(stepId, currentStep.getId());
        }

        // Polymorphic call - DTO converts itself to Map
        Map<String, Object> responseData = dto.toResponseData();

        // Delegate to internal method
        return submitResponseInternal(session, stepId, participant, responseData);
    }

    /**
     * Internal method that handles the actual response creation/update logic.
     * Called by the polymorphic submitResponse method after validation.
     */
    private ParticipantResponse submitResponseInternal(
            RetroSession session,
            Long stepId,
            Participant participant,
            Map<String, Object> responseData) {

        if (responseData == null || responseData.isEmpty()) {
            throw new IllegalArgumentException("Response data cannot be empty");
        }

        RetroStep step = getRetroStepById(stepId);

        // Enforce 10-input limit for MULTI_COLUMN_BOARD (not for RATING_SCALE which has 1-per-participant)
        if (step.getComponentType() == ComponentType.MULTI_COLUMN_BOARD) {
            Long existingCount = responseRepository.countByParticipantSessionAndStep(participant, session, step);
            int inputLimit = 10;
            if (existingCount >= inputLimit) {
                throw new InputLimitExceededException(existingCount, inputLimit);
            }
        }

        ParticipantResponse response;

        // For RATING_SCALE: update existing response if present (one rating per participant)
        // For MULTI_COLUMN_BOARD: always create new response (multiple cards allowed)
        if (step.getComponentType() == ComponentType.RATING_SCALE) {
            Optional<ParticipantResponse> existing = responseRepository
                .findBySessionAndRetroStepAndParticipant(session, step, participant);

            if (existing.isPresent()) {
                response = existing.get();
                response.getResponseData().putAll(responseData);
                response.setEditedAt(LocalDateTime.now());
                log.info("Updated rating response for participant {} in step {}", participant.getDisplayName(), stepId);
            } else {
                response = new ParticipantResponse();
                response.setRetroStep(step);
                response.setParticipant(participant);
                // RATING_SCALE responses are public by default (shown in histogram)
                response.setIsVisible(true);
                response.getResponseData().putAll(responseData);
                log.info("Created rating response for participant {} in step {}", participant.getDisplayName(), stepId);
            }
        } else {
            // MULTI_COLUMN_BOARD or other types - always create new response
            response = new ParticipantResponse();
            response.setRetroStep(step);
            response.setParticipant(participant);
            response.setIsVisible(false);
            response.getResponseData().putAll(responseData);
            log.info("Created response for participant {} in step {} (component type: {})",
                participant.getDisplayName(), stepId, step.getComponentType());
        }

        ParticipantResponse savedResponse = responseRepository.save(response);
        retroSyncVersionService.bumpSyncVersion(session.getId());
        publishResponseSubmittedEvent(session.getId(), savedResponse);

        return savedResponse;
    }

    public ParticipantResponse updateResponse(UUID responseId, Participant participant, String newContent) {
        ParticipantResponse response = responseRepository.findById(responseId)
            .orElseThrow(() -> new IllegalArgumentException("Response not found"));

        // Check ownership
        if (!response.getParticipant().getParticipantId().equals(participant.getParticipantId())) {
            throw new SecurityException("Cannot edit another participant's response");
        }

        response.getResponseData().put("content", newContent);
        response.setEditedAt(LocalDateTime.now());

        ParticipantResponse saved = responseRepository.save(response);
        retroSyncVersionService.bumpSyncVersion(response.getParticipant().getSession().getId());
        log.info("Updated response {} by participant {}", responseId, participant.getDisplayName());

        publishResponseSubmittedEvent(response.getParticipant().getSession().getId(), saved);

        return saved;
    }

    public List<ParticipantResponse> getResponsesForStep(RetroSession session, RetroStep step) {
        return responseRepository.findBySessionAndRetroStep(session, step);
    }

    public List<ParticipantResponse> getVisibleResponsesForStep(RetroSession session, RetroStep step) {
        return responseRepository.findVisibleBySessionAndRetroStep(session, step);
    }

    /**
     * Get responses for a specific component type within a stage.
     * Used by display components (e.g., HISTOGRAM_CHART) to retrieve responses
     * from input components (e.g., RATING_SCALE) within the same stage.
     */
    public List<ParticipantResponse> getResponsesForStageComponentType(
            RetroSession session,
            RetroStage stage,
            ComponentType componentType) {

        log.debug("Querying responses for session {}, stage {}, component type {}",
            session.getId(), stage.getName(), componentType);

        return responseRepository.findBySessionAndStageAndComponentType(session, stage, componentType);
    }

    public Long getParticipationCount(RetroSession session, RetroStep step) {
        return responseRepository.countResponsesForStep(session, step);
    }

    public void revealAllResponses(RetroSession session, Long stepId) {
        RetroStep step = getRetroStepById(stepId);

        List<ParticipantResponse> responses = responseRepository.findBySessionAndRetroStep(session, step);
        responses.forEach(response -> response.setIsVisible(true));
        responseRepository.saveAll(responses);
        retroSyncVersionService.bumpSyncVersion(session.getId());

        log.info("Revealed {} responses for step {} in session {}", responses.size(), stepId, session.getId());

        try {
            eventService.publish(RetroEvent.responsesRevealed(session.getId(), "facilitator", stepId));
        } catch (Exception eventError) {
            log.error("Failed to publish responses revealed event: {}", eventError.getMessage());
        }
    }

    /**
     * Toggle vote on a response (add if not present, remove if already voted).
     * Each participant can vote once per response.
     * Enforces vote limits from step capabilities across all responses in the step.
     */
    public ParticipantResponse toggleVote(UUID retroId, UUID responseId, HttpServletRequest request) {
        // Validate participant access
        Participant participant = participantService.getParticipantForSession(request, retroId);

        // Find the response
        ParticipantResponse response = responseRepository.findById(responseId)
            .orElseThrow(() -> new IllegalArgumentException("Response not found"));

        // Verify response belongs to the same session
        if (!response.getParticipant().getSession().getId().equals(retroId)) {
            throw new SecurityException("Response does not belong to this session");
        }

        // Get or initialize votes list
        Map<String, Object> responseData = response.getResponseData();
        Object votesObj = responseData.get("votes");
        List<String> votes;

        if (votesObj instanceof List<?>) {
            votes = (List<String>) votesObj;
        } else {
            votes = new java.util.ArrayList<>();
            responseData.put("votes", votes);
        }

        String participantIdStr = participant.getParticipantId().toString();

        // Toggle vote
        if (votes.contains(participantIdStr)) {
            // Remove vote (retraction)
            votes.remove(participantIdStr);
            log.info("Participant {} retracted vote from response {}", participant.getDisplayName(), responseId);
        } else {
            // Check vote limit before adding
            RetroStep currentStep = response.getRetroStep();
            Map<String, Object> capabilities = (Map<String, Object>) currentStep.getComponentConfig().get("capabilities");
            Integer numberOfVotes = capabilities != null ? (Integer) capabilities.get("numberOfVotes") : 5;

            // Count how many votes this participant has used across all responses in this step
            List<ParticipantResponse> stepResponses = responseRepository
                .findBySessionAndRetroStep(response.getParticipant().getSession(), currentStep);

            long votesUsed = stepResponses.stream()
                .mapToLong(r -> {
                    Object v = r.getResponseData().get("votes");
                    if (v instanceof List<?>) {
                        List<String> voteList = (List<String>) v;
                        return voteList.stream().filter(id -> id.equals(participantIdStr)).count();
                    }
                    return 0;
                })
                .sum();

            if (votesUsed >= numberOfVotes) {
                throw new VoteLimitExceededException(votesUsed, numberOfVotes);
            }

            // Add vote
            votes.add(participantIdStr);
            log.info("Participant {} voted on response {}", participant.getDisplayName(), responseId);
        }

        response.setEditedAt(LocalDateTime.now());
        ParticipantResponse saved = responseRepository.save(response);
        retroSyncVersionService.bumpSyncVersion(retroId);

        // Publish event to refresh UI for all participants
        publishResponseSubmittedEvent(retroId, saved);

        return saved;
    }

    public Optional<ParticipantResponse> getMyRatingResponse(
            UUID retroId,
            Long stepId,
            HttpServletRequest request) {

        Participant participant = participantService.getParticipantForSession(request, retroId);
        RetroSession session = participant.getSession();
        RetroStep step = getRetroStepById(stepId);

        return responseRepository.findBySessionAndRetroStepAndParticipant(session, step, participant);
    }

    private RetroStep getRetroStepById(Long stepId) {
        return retroStepQueryService.getStepById(stepId);
    }

    private void publishResponseSubmittedEvent(UUID retroId, ParticipantResponse response) {
        try {
            String summary = response.getResponseData().toString();
            if (summary.length() > 100) {
                summary = summary.substring(0, 97) + "...";
            }

            RetroEvent.ResponseData responseData = new RetroEvent.ResponseData(
                response.getId().toString(),
                response.getRetroStep().getId(),
                response.getParticipant().getParticipantId().toString(),
                response.getParticipant().getDisplayName(),
                summary,
                response.getIsVisible(),
                response.getSubmittedAt().atZone(ZoneId.systemDefault()).toInstant()
            );

            RetroEvent<RetroEvent.ResponseData> event =
                RetroEvent.responseSubmitted(retroId,
                    response.getParticipant().getParticipantId().toString(),
                    responseData);

            log.debug("[{}] Publishing NOTE_ADDED event for participant {} in step {}",
                event.correlationId(),
                response.getParticipant().getDisplayName(),
                response.getRetroStep().getId());

            eventService.publish(event);

        } catch (Exception eventError) {
            log.error("Failed to publish response submitted event: {}", eventError.getMessage(), eventError);
        }
    }
}
