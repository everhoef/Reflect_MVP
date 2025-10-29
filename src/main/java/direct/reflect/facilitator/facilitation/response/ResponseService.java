package direct.reflect.facilitator.facilitation.response;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import direct.reflect.facilitator.facilitation.RetroSession;
import direct.reflect.facilitator.facilitation.Participant;
import direct.reflect.facilitator.configurator.RetroStep;
import direct.reflect.facilitator.configurator.RetroStepRepository;
import direct.reflect.facilitator.configurator.DataPattern;
import direct.reflect.facilitator.eventing.EventService;
import direct.reflect.facilitator.eventing.RetroEvent;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ResponseService {

    private final ParticipantResponseRepository responseRepository;
    private final RetroStepRepository retroStepRepository;
    private final EventService eventService;

    public ParticipantResponse submitResponse(
            RetroSession session,
            Long stepId,
            Participant participant,
            DataPattern dataPattern,
            Map<String, Object> responseData) {

        if (responseData == null || responseData.isEmpty()) {
            throw new IllegalArgumentException("Response data cannot be empty");
        }

        RetroStep step = getRetroStepById(stepId);

        // For RATING pattern, allow updates (one rating per participant per step)
        Optional<ParticipantResponse> existing = responseRepository
            .findBySessionAndRetroStepAndParticipant(session, step, participant);

        ParticipantResponse response;
        if (existing.isPresent() && existing.get().getDataPattern() == dataPattern && dataPattern == DataPattern.RATING) {
            response = existing.get();
            response.getResponseData().putAll(responseData);
            response.setEditedAt(LocalDateTime.now());
            log.info("Updated {} response for participant {} in step {}", dataPattern, participant.getDisplayName(), stepId);
        } else {
            response = new ParticipantResponse();
            response.setRetroStep(step);
            response.setParticipant(participant);
            response.setDataPattern(dataPattern);
            response.setIsVisible(false);
            response.getResponseData().putAll(responseData);
            log.info("Submitted {} response for participant {} in step {}", dataPattern, participant.getDisplayName(), stepId);
        }

        ParticipantResponse savedResponse = responseRepository.save(response);
        publishResponseSubmittedEvent(session.getId(), savedResponse);

        return savedResponse;
    }

    public List<ParticipantResponse> getResponsesForStep(RetroSession session, RetroStep step) {
        return responseRepository.findBySessionAndRetroStep(session, step);
    }

    public List<ParticipantResponse> getVisibleResponsesForStep(RetroSession session, RetroStep step) {
        return responseRepository.findVisibleBySessionAndRetroStep(session, step);
    }

    public Long getParticipationCount(RetroSession session, RetroStep step) {
        return responseRepository.countResponsesForStep(session, step);
    }

    public void revealAllResponses(RetroSession session, Long stepId) {
        RetroStep step = getRetroStepById(stepId);

        List<ParticipantResponse> responses = responseRepository.findBySessionAndRetroStep(session, step);
        responses.forEach(response -> response.setIsVisible(true));
        responseRepository.saveAll(responses);

        log.info("Revealed {} responses for step {} in session {}", responses.size(), stepId, session.getId());

        try {
            eventService.publish(RetroEvent.responsesRevealed(session.getId(), "facilitator", stepId));
        } catch (Exception eventError) {
            log.error("Failed to publish responses revealed event: {}", eventError.getMessage());
        }
    }

    private RetroStep getRetroStepById(Long stepId) {
        return retroStepRepository.findById(stepId)
            .orElseThrow(() -> new IllegalArgumentException("RetroStep not found with ID: " + stepId));
    }

    private void publishResponseSubmittedEvent(UUID retroId, ParticipantResponse response) {
        try {
            RetroEvent.ResponseData responseData = new RetroEvent.ResponseData(
                response.getId().toString(),
                response.getRetroStep().getId(),
                response.getParticipant().getParticipantId().toString(),
                response.getParticipant().getDisplayName(),
                response.getDisplaySummary(),
                response.getIsVisible(),
                response.getSubmittedAt().atZone(ZoneId.systemDefault()).toInstant()
            );
            eventService.publish(RetroEvent.responseSubmitted(retroId, response.getParticipant().getParticipantId().toString(), responseData));
        } catch (Exception eventError) {
            log.error("Failed to publish response submitted event: {}", eventError.getMessage());
        }
    }
}
