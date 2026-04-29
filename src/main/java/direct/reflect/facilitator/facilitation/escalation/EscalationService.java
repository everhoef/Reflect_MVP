package direct.reflect.facilitator.facilitation.escalation;

import direct.reflect.facilitator.auth.AuthService;
import direct.reflect.facilitator.eventing.EventService;
import direct.reflect.facilitator.eventing.RetroEvent;
import direct.reflect.facilitator.facilitation.participant.Participant;
import direct.reflect.facilitator.facilitation.participant.ParticipantRepository;
import direct.reflect.facilitator.facilitation.participant.ParticipantRole;
import direct.reflect.facilitator.facilitation.participant.ParticipantStatus;
import direct.reflect.facilitator.facilitation.participant.ParticipantService;
import direct.reflect.facilitator.facilitation.session.RetroSession;
import direct.reflect.facilitator.facilitation.session.RetroSyncVersionService;
import direct.reflect.facilitator.facilitation.actions.ActionItem;
import direct.reflect.facilitator.facilitation.actions.ActionItemNotFoundException;
import direct.reflect.facilitator.facilitation.actions.ActionItemRepository;
import direct.reflect.facilitator.facilitation.escalation.domain.EscalationThresholdPolicy;
import direct.reflect.facilitator.organization.ManagerTeamAccess;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class EscalationService {

    private final ActionItemRepository actionItemRepository;
    private final EscalatedItemRepository escalatedItemRepository;
    private final EscalatedItemVoteRepository escalatedItemVoteRepository;
    private final ParticipantService participantService;
    private final ParticipantRepository participantRepository;
    private final EventService eventService;
    private final RetroSyncVersionService retroSyncVersionService;
    private final ManagerTeamAccess managerTeamAccess;
    private final AuthService authService;

    public EscalatedItemDto escalateAction(
            UUID retroId,
            UUID actionId,
            String problemDescription,
            HttpServletRequest request) {
        participantService.getParticipantForSession(request, retroId);

        ActionItem actionItem = actionItemRepository.findByIdAndRetroSessionId(actionId, retroId)
                .orElseThrow(() -> new ActionItemNotFoundException(actionId));

        if (Boolean.TRUE.equals(actionItem.getEscalated())) {
            throw new IllegalArgumentException("Action item already escalated");
        }

        RetroSession retroSession = actionItem.getRetroSession();
        if (retroSession.getTeamId() == null) {
            throw new IllegalArgumentException("Retro session must belong to a team before escalation");
        }

        int participantCount = participantService.getSessionParticipants(retroId).size();
        EscalatedItem escalatedItem = new EscalatedItem();
        escalatedItem.setRetroSession(retroSession);
        escalatedItem.setTeamId(retroSession.getTeamId());
        escalatedItem.setProblemDescription(problemDescription);
        escalatedItem.setVoteThreshold(EscalationThresholdPolicy.calculateVoteThreshold(participantCount));

        EscalatedItem savedEscalatedItem = escalatedItemRepository.save(escalatedItem);

        actionItem.setEscalated(true);
        actionItemRepository.save(actionItem);
        retroSyncVersionService.bumpSyncVersion(retroId);

        Participant participant = participantService.getParticipantForSession(request, retroId);
        eventService.publish(RetroEvent.escalationCreated(
                retroId,
                participant.getParticipantId().toString(),
                savedEscalatedItem.getId().toString()));

        return EscalatedItemDto.from(savedEscalatedItem, 0, false);
    }

    public EscalationVoteResultDto toggleVote(UUID retroId, UUID escalationId, HttpServletRequest request) {
        Participant participant = participantService.getParticipantForSession(request, retroId);
        EscalatedItem escalatedItem = escalatedItemRepository.findByIdAndRetroSession_Id(escalationId, retroId)
                .orElseThrow(() -> new EscalatedItemNotFoundException(escalationId));

        EscalatedItemVote existingVote = escalatedItemVoteRepository
                .findByEscalatedItemIdAndParticipantId(escalationId, participant.getParticipantId())
                .orElse(null);

        boolean voted;
        if (existingVote != null) {
            escalatedItemVoteRepository.delete(existingVote);
            voted = false;
        } else {
            EscalatedItemVote vote = new EscalatedItemVote();
            vote.setId(new EscalatedItemVoteId(escalationId, participant.getParticipantId()));
            vote.setEscalatedItem(escalatedItem);
            escalatedItemVoteRepository.save(vote);
            voted = true;
        }

        long voteCount = escalatedItemVoteRepository.countByEscalatedItemId(escalationId);
        boolean thresholdMet = isThresholdMet(escalatedItem, voteCount);
        retroSyncVersionService.bumpSyncVersion(retroId);

        RetroEvent.EscalationVoteData payload = new RetroEvent.EscalationVoteData(
                escalationId.toString(),
                voteCount,
                escalatedItem.getVoteThreshold(),
                thresholdMet);
        eventService.publish(RetroEvent.escalationVoteUpdated(retroId, participant.getParticipantId().toString(), payload));

        return new EscalationVoteResultDto(
                retroSyncVersionService.getSyncVersion(retroId),
                escalationId,
                voteCount,
                escalatedItem.getVoteThreshold(),
                thresholdMet,
                voted);
    }

    @Transactional(readOnly = true)
    public List<EscalatedItemDto> getEscalations(UUID retroId, HttpServletRequest request) {
        participantService.getParticipantForSession(request, retroId);

        long syncVersion = retroSyncVersionService.getSyncVersion(retroId);
        List<EscalatedItem> escalatedItems = escalatedItemRepository.findByRetroSession_IdOrderByCreatedAtAsc(retroId);

        return toEscalatedItemDtos(escalatedItems).stream()
                .map(escalatedItemDto -> escalatedItemDto.withSyncVersion(syncVersion))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EscalatedItemDto> getManagerEscalations(Authentication authentication) {
        List<UUID> managedTeamIds = getManagedTeamIds(authentication);
        if (managedTeamIds.isEmpty()) {
            return List.of();
        }

        List<EscalatedItem> escalatedItems = escalatedItemRepository.findByTeamIdInOrderByCreatedAtAsc(managedTeamIds);

        return toEscalatedItemDtos(escalatedItems).stream()
                .filter(EscalatedItemDto::thresholdMet)
                .toList();
    }

    @Transactional(readOnly = true)
    public EscalatedItemDto getManagerEscalation(UUID escalationId, Authentication authentication) {
        List<UUID> managedTeamIds = getManagedTeamIds(authentication);
        if (managedTeamIds.isEmpty()) {
            throw new EscalatedItemNotFoundException(escalationId);
        }

        EscalatedItem escalatedItem = escalatedItemRepository.findByIdAndTeamIdIn(escalationId, managedTeamIds)
                .orElseThrow(() -> new EscalatedItemNotFoundException(escalationId));

        EscalatedItemDto dto = toEscalatedItemDto(escalatedItem);
        if (!dto.thresholdMet()) {
            throw new EscalatedItemNotFoundException(escalationId);
        }

        return dto;
    }

    private EscalatedItemDto toEscalatedItemDto(EscalatedItem escalatedItem) {
        long voteCount = escalatedItemVoteRepository.countByEscalatedItemId(escalatedItem.getId());
        return EscalatedItemDto.from(escalatedItem, voteCount, isThresholdMet(escalatedItem, voteCount));
    }

    private List<EscalatedItemDto> toEscalatedItemDtos(List<EscalatedItem> escalatedItems) {
        if (escalatedItems.isEmpty()) {
            return List.of();
        }

        Map<UUID, Long> voteCountsByEscalationId = getVoteCountsByEscalationId(escalatedItems);
        Map<UUID, List<Participant>> participantsBySessionId = getParticipantsBySessionIdForTieBreaks(escalatedItems, voteCountsByEscalationId);

        return escalatedItems.stream()
                .map(escalatedItem -> {
                    long voteCount = voteCountsByEscalationId.getOrDefault(escalatedItem.getId(), 0L);
                    List<Participant> participants = participantsBySessionId.getOrDefault(
                            escalatedItem.getRetroSession().getId(),
                            List.of());
                    return EscalatedItemDto.from(
                            escalatedItem,
                            voteCount,
                            isThresholdMet(escalatedItem, voteCount, participants));
                })
                .toList();
    }

    private boolean isThresholdMet(EscalatedItem escalatedItem, long voteCount, List<Participant> participants) {
        int threshold = escalatedItem.getVoteThreshold();
        if (EscalationThresholdPolicy.hasReachedThreshold(voteCount, threshold)) {
            return true;
        }

        if (!EscalationThresholdPolicy.isTieBreakScenario(voteCount, threshold)) {
            return false;
        }

        return isThresholdMet(voteCount, participants, escalatedItem.getId());
    }

    private boolean isThresholdMet(EscalatedItem escalatedItem, long voteCount) {
        int threshold = escalatedItem.getVoteThreshold();
        if (EscalationThresholdPolicy.hasReachedThreshold(voteCount, threshold)) {
            return true;
        }

        if (!EscalationThresholdPolicy.isTieBreakScenario(voteCount, threshold)) {
            return false;
        }

        List<Participant> participants = participantService.getSessionParticipants(escalatedItem.getRetroSession().getId());
        return isThresholdMet(voteCount, participants, escalatedItem.getId());
    }

    private boolean isThresholdMet(long voteCount, List<Participant> participants, UUID escalationId) {
        boolean facilitatorVoted = facilitatorHasVoted(escalationId, participants);

        return EscalationThresholdPolicy.facilitatorTieBreakApplies(participants.size(), facilitatorVoted);
    }

    private Map<UUID, Long> getVoteCountsByEscalationId(List<EscalatedItem> escalatedItems) {
        List<UUID> escalationIds = escalatedItems.stream()
                .map(EscalatedItem::getId)
                .toList();

        return escalatedItemVoteRepository.countByEscalatedItemIdIn(escalationIds).stream()
                .collect(HashMap::new, (counts, row) -> counts.put((UUID) row[0], (Long) row[1]), HashMap::putAll);
    }

    private Map<UUID, List<Participant>> getParticipantsBySessionIdForTieBreaks(
            List<EscalatedItem> escalatedItems,
            Map<UUID, Long> voteCountsByEscalationId) {
        List<UUID> sessionIdsRequiringTieBreakCheck = escalatedItems.stream()
                .filter(escalatedItem -> EscalationThresholdPolicy.isTieBreakScenario(
                        voteCountsByEscalationId.getOrDefault(escalatedItem.getId(), 0L),
                        escalatedItem.getVoteThreshold()))
                .map(escalatedItem -> escalatedItem.getRetroSession().getId())
                .distinct()
                .toList();

        if (sessionIdsRequiringTieBreakCheck.isEmpty()) {
            return Collections.emptyMap();
        }

        return groupParticipantsBySessionId(
                participantRepository.findBySession_IdInAndStatus(sessionIdsRequiringTieBreakCheck, ParticipantStatus.ACTIVE));
    }

    private Map<UUID, List<Participant>> groupParticipantsBySessionId(Collection<Participant> participants) {
        Map<UUID, List<Participant>> participantsBySessionId = new HashMap<>();
        for (Participant participant : participants) {
            participantsBySessionId
                    .computeIfAbsent(participant.getSession().getId(), ignored -> new ArrayList<>())
                    .add(participant);
        }
        return participantsBySessionId;
    }

    private boolean facilitatorHasVoted(UUID escalationId, List<Participant> participants) {
        return participants.stream()
                .filter(participant -> participant.getRole() == ParticipantRole.FACILITATOR)
                .findFirst()
                .map(Participant::getParticipantId)
                .flatMap(facilitatorId -> escalatedItemVoteRepository.findByEscalatedItemIdAndParticipantId(
                        escalationId,
                        facilitatorId))
                .isPresent();
    }

    private List<UUID> getManagedTeamIds(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return List.of();
        }

        UUID userId = authService.toOidcUserId(authentication.getName());
        return managerTeamAccess.findManagedTeamIds(userId);
    }
}
