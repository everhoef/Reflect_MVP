package direct.reflect.facilitator.facilitation.actionitem;

import direct.reflect.facilitator.common.exception.ResourceNotFoundException;
import direct.reflect.facilitator.eventing.EventService;
import direct.reflect.facilitator.eventing.RetroEvent;
import direct.reflect.facilitator.facilitation.Participant;
import direct.reflect.facilitator.facilitation.ParticipantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ActionItemService {

    private final ActionItemRepository actionItemRepository;
    private final ParticipantRepository participantRepository;
    private final EventService eventService;

    public ActionItemDto createActionItem(UUID retroSessionId, Long stepId, ActionItemDto dto) {
        if (dto.getWhat() == null || dto.getWhat().isBlank()) {
            throw new IllegalArgumentException("Action item 'what' field must not be blank");
        }

        ActionItem actionItem = ActionItem.builder()
                .what(dto.getWhat())
                .assignedToParticipantId(dto.getAssignedToParticipantId())
                .whenDate(dto.getWhenDate())
                .successCriteria(dto.getSuccessCriteria())
                .retroSessionId(retroSessionId)
                .retroStepId(stepId)
                .createdByParticipantId(dto.getCreatedByParticipantId())
                .build();

        ActionItem saved = actionItemRepository.save(actionItem);
        log.debug("Created action item {} for session {}", saved.getId(), retroSessionId);

        try {
            eventService.publish(new RetroEvent<>(
                    "evt-" + UUID.randomUUID().toString().substring(0, 8),
                    retroSessionId,
                    RetroEvent.EventType.NOTE_ADDED,
                    "system",
                    Instant.now(),
                    saved.getId().toString()));
        } catch (Exception e) {
            log.debug("Failed to publish action item created event: {}", e.getMessage());
        }

        return toDto(saved);
    }

    public ActionItemDto updateActionItem(UUID retroId, UUID actionItemId, ActionItemDto dto) {
        ActionItem actionItem = actionItemRepository.findById(actionItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Action item not found: " + actionItemId));

        if (!actionItem.getRetroSessionId().equals(retroId)) {
            throw new ResourceNotFoundException("Action item not found: " + actionItemId);
        }

        if (dto.getWhat() != null) {
            actionItem.setWhat(dto.getWhat());
        }
        actionItem.setAssignedToParticipantId(dto.getAssignedToParticipantId());
        actionItem.setWhenDate(dto.getWhenDate());
        actionItem.setSuccessCriteria(dto.getSuccessCriteria());

        ActionItem saved = actionItemRepository.save(actionItem);
        log.debug("Updated action item {}", actionItemId);

        try {
            eventService.publish(new RetroEvent<>(
                    "evt-" + UUID.randomUUID().toString().substring(0, 8),
                    saved.getRetroSessionId(),
                    RetroEvent.EventType.NOTE_UPDATED,
                    "system",
                    Instant.now(),
                    saved.getId().toString()));
        } catch (Exception e) {
            log.debug("Failed to publish action item updated event: {}", e.getMessage());
        }

        return toDto(saved);
    }

    public void deleteActionItem(UUID retroId, UUID actionItemId) {
        ActionItem actionItem = actionItemRepository.findById(actionItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Action item not found: " + actionItemId));

        if (!actionItem.getRetroSessionId().equals(retroId)) {
            throw new ResourceNotFoundException("Action item not found: " + actionItemId);
        }

        UUID retroSessionId = actionItem.getRetroSessionId();
        actionItemRepository.delete(actionItem);
        log.debug("Deleted action item {}", actionItemId);

        try {
            eventService.publish(new RetroEvent<>(
                    "evt-" + UUID.randomUUID().toString().substring(0, 8),
                    retroSessionId,
                    RetroEvent.EventType.NOTE_DELETED,
                    "system",
                    Instant.now(),
                    actionItemId.toString()));
        } catch (Exception e) {
            log.debug("Failed to publish action item deleted event: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<ActionItemDto> getActionItemsBySession(UUID retroSessionId) {
        List<ActionItem> items = actionItemRepository.findByRetroSessionId(retroSessionId);

        // Collect all assignedToParticipantIds to resolve display names in a single query (avoid N+1)
        List<UUID> assignedIds = items.stream()
                .map(ActionItem::getAssignedToParticipantId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());

        Map<UUID, String> displayNameByParticipantId = participantRepository
                .findByParticipantIdIn(assignedIds)
                .stream()
                .collect(Collectors.toMap(
                        Participant::getParticipantId,
                        Participant::getDisplayName,
                        (a, b) -> a  // keep first if duplicate participantId across sessions
                ));

        return items.stream()
                .map(item -> toDto(item, displayNameByParticipantId))
                .collect(Collectors.toList());
    }

    private ActionItemDto toDto(ActionItem actionItem) {
        String assignedToDisplayName = null;
        if (actionItem.getAssignedToParticipantId() != null) {
            List<Participant> participants = participantRepository
                    .findByParticipantId(actionItem.getAssignedToParticipantId());
            if (!participants.isEmpty()) {
                assignedToDisplayName = participants.get(0).getDisplayName();
            }
        }

        return ActionItemDto.builder()
                .id(actionItem.getId())
                .what(actionItem.getWhat())
                .assignedToParticipantId(actionItem.getAssignedToParticipantId())
                .assignedToDisplayName(assignedToDisplayName)
                .whenDate(actionItem.getWhenDate())
                .successCriteria(actionItem.getSuccessCriteria())
                .retroSessionId(actionItem.getRetroSessionId())
                .retroStepId(actionItem.getRetroStepId())
                .createdByParticipantId(actionItem.getCreatedByParticipantId())
                .createdAt(actionItem.getCreatedAt())
                .build();
    }

    private ActionItemDto toDto(ActionItem actionItem, Map<UUID, String> displayNameByParticipantId) {
        String assignedToDisplayName = actionItem.getAssignedToParticipantId() != null
                ? displayNameByParticipantId.get(actionItem.getAssignedToParticipantId())
                : null;

        return ActionItemDto.builder()
                .id(actionItem.getId())
                .what(actionItem.getWhat())
                .assignedToParticipantId(actionItem.getAssignedToParticipantId())
                .assignedToDisplayName(assignedToDisplayName)
                .whenDate(actionItem.getWhenDate())
                .successCriteria(actionItem.getSuccessCriteria())
                .retroSessionId(actionItem.getRetroSessionId())
                .retroStepId(actionItem.getRetroStepId())
                .createdByParticipantId(actionItem.getCreatedByParticipantId())
                .createdAt(actionItem.getCreatedAt())
                .build();
    }
}
