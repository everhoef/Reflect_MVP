package direct.reflect.facilitator.facilitation.actions;

import direct.reflect.facilitator.common.exception.ResourceNotFoundException;
import direct.reflect.facilitator.eventing.EventService;
import direct.reflect.facilitator.eventing.RetroEvent;
import direct.reflect.facilitator.facilitation.Participant;
import direct.reflect.facilitator.facilitation.ParticipantService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ActionItemService {

    private final ActionItemRepository actionItemRepository;
    private final ParticipantService participantService;
    private final EventService eventService;

    public ActionItemDto createActionItem(
            UUID retroId,
            CreateActionItemRequest request,
            HttpServletRequest httpRequest) {
        Participant participant = participantService.getParticipantForSession(httpRequest, retroId);

        ActionItem actionItem = new ActionItem();
        actionItem.setRetroSession(participant.getSession());
        actionItem.setCreatedByParticipantId(participant.getParticipantId());
        actionItem.setWhat(normalizeRequiredText(request.what(), "what"));
        actionItem.setWho(normalizeRequiredText(request.who(), "who"));
        actionItem.setDueDate(request.dueDate());
        actionItem.setSuccessCriteria(normalizeOptionalText(request.successCriteria()));

        ActionItem savedActionItem = actionItemRepository.save(actionItem);
        ActionItemDto actionItemDto = ActionItemDto.from(savedActionItem);

        publishActionEvent(retroId, participant.getParticipantId(), RetroEvent.EventType.ACTION_CREATED, actionItemDto);
        log.debug("Created action item {} for retro {}", savedActionItem.getId(), retroId);

        return actionItemDto;
    }

    @Transactional(readOnly = true)
    public List<ActionItemDto> getActionItems(UUID retroId) {
        return actionItemRepository.findByRetroSessionId(retroId)
                .stream()
                .map(ActionItemDto::from)
                .toList();
    }

    public ActionItemDto updateActionItem(
            UUID retroId,
            UUID actionId,
            UpdateActionItemRequest request,
            HttpServletRequest httpRequest) {
        Participant participant = participantService.getParticipantForSession(httpRequest, retroId);

        if (!request.hasChanges()) {
            throw new IllegalArgumentException("At least one action item field must be provided");
        }

        ActionItem actionItem = getActionItemOrThrow(retroId, actionId);

        if (request.what() != null) {
            actionItem.setWhat(normalizeRequiredText(request.what(), "what"));
        }
        if (request.who() != null) {
            actionItem.setWho(normalizeRequiredText(request.who(), "who"));
        }
        if (request.dueDate() != null) {
            actionItem.setDueDate(request.dueDate());
        }
        if (request.successCriteria() != null) {
            actionItem.setSuccessCriteria(normalizeOptionalText(request.successCriteria()));
        }

        ActionItem savedActionItem = actionItemRepository.save(actionItem);
        ActionItemDto actionItemDto = ActionItemDto.from(savedActionItem);

        publishActionEvent(retroId, participant.getParticipantId(), RetroEvent.EventType.ACTION_UPDATED, actionItemDto);
        log.debug("Updated action item {} for retro {}", actionId, retroId);

        return actionItemDto;
    }

    public void deleteActionItem(UUID retroId, UUID actionId, HttpServletRequest httpRequest) {
        Participant participant = participantService.getParticipantForSession(httpRequest, retroId);
        ActionItem actionItem = getActionItemOrThrow(retroId, actionId);
        ActionItemDto deletedActionItem = ActionItemDto.from(actionItem);

        actionItemRepository.delete(actionItem);
        publishActionEvent(retroId, participant.getParticipantId(), RetroEvent.EventType.ACTION_DELETED, deletedActionItem);
        log.debug("Deleted action item {} for retro {}", actionId, retroId);
    }

    public ActionItemDto updateActionItemStatus(
            UUID retroId,
            UUID actionId,
            UpdateActionItemStatusRequest request,
            HttpServletRequest httpRequest) {
        Participant participant = participantService.getParticipantForSession(httpRequest, retroId);
        ActionItem actionItem = getActionItemOrThrow(retroId, actionId);

        actionItem.setStatus(request.status());

        ActionItem savedActionItem = actionItemRepository.save(actionItem);
        ActionItemDto actionItemDto = ActionItemDto.from(savedActionItem);

        publishActionEvent(retroId, participant.getParticipantId(), RetroEvent.EventType.ACTION_UPDATED, actionItemDto);
        log.debug("Updated action item {} status to {} for retro {}", actionId, request.status(), retroId);

        return actionItemDto;
    }

    private ActionItem getActionItemOrThrow(UUID retroId, UUID actionId) {
        return actionItemRepository.findByIdAndRetroSessionId(actionId, retroId)
                .orElseThrow(() -> new ResourceNotFoundException("Action item not found: " + actionId));
    }

    private void publishActionEvent(
            UUID retroId,
            UUID participantId,
            RetroEvent.EventType eventType,
            ActionItemDto actionItemDto) {
        eventService.publish(new RetroEvent<>(
                "evt-" + UUID.randomUUID().toString().substring(0, 8),
                retroId,
                eventType,
                participantId.toString(),
                Instant.now(),
                actionItemDto));
    }

    private String normalizeRequiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Action item '" + fieldName + "' is required");
        }
        return value.trim();
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }

        String trimmedValue = value.trim();
        return trimmedValue.isEmpty() ? null : trimmedValue;
    }
}
