package direct.reflect.facilitator.facilitation.escalation;

import direct.reflect.facilitator.auth.AuthService;
import direct.reflect.facilitator.common.exception.ResourceNotFoundException;
import direct.reflect.facilitator.eventing.EventService;
import direct.reflect.facilitator.eventing.RetroEvent;
import direct.reflect.facilitator.facilitation.Participant;
import direct.reflect.facilitator.facilitation.ParticipantRole;
import direct.reflect.facilitator.facilitation.ParticipantService;
import direct.reflect.facilitator.facilitation.RetroSession;
import direct.reflect.facilitator.facilitation.actions.ActionItem;
import direct.reflect.facilitator.facilitation.actions.ActionItemRepository;
import direct.reflect.facilitator.organization.TeamMemberRepository;
import direct.reflect.facilitator.organization.TeamRole;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
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
    private final EventService eventService;
    private final TeamMemberRepository teamMemberRepository;
    private final AuthService authService;

    public EscalatedItemDto escalateAction(
            UUID retroId,
            UUID actionId,
            String problemDescription,
            HttpServletRequest request) {
        participantService.getParticipantForSession(request, retroId);

        ActionItem actionItem = actionItemRepository.findByIdAndRetroSessionId(actionId, retroId)
                .orElseThrow(() -> new ResourceNotFoundException("Action item not found"));

        if (Boolean.TRUE.equals(actionItem.getEscalated())) {
            throw new IllegalArgumentException("Action item already escalated");
        }

        RetroSession retroSession = actionItem.getRetroSession();
        if (retroSession.getTeam() == null) {
            throw new IllegalArgumentException("Retro session must belong to a team before escalation");
        }

        int participantCount = participantService.getSessionParticipants(retroId).size();
        EscalatedItem escalatedItem = new EscalatedItem();
        escalatedItem.setRetroSession(retroSession);
        escalatedItem.setTeam(retroSession.getTeam());
        escalatedItem.setProblemDescription(problemDescription);
        escalatedItem.setVoteThreshold(EscalatedItem.calculateVoteThreshold(participantCount));

        EscalatedItem savedEscalatedItem = escalatedItemRepository.save(escalatedItem);

        actionItem.setEscalated(true);
        actionItemRepository.save(actionItem);

        return EscalatedItemDto.from(savedEscalatedItem, 0, false);
    }

    public EscalationVoteResultDto toggleVote(UUID retroId, UUID escalationId, HttpServletRequest request) {
        Participant participant = participantService.getParticipantForSession(request, retroId);
        EscalatedItem escalatedItem = escalatedItemRepository.findByIdAndRetroSession_Id(escalationId, retroId)
                .orElseThrow(() -> new ResourceNotFoundException("Escalated item not found"));

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

        RetroEvent.EscalationVoteData payload = new RetroEvent.EscalationVoteData(
                escalationId.toString(),
                voteCount,
                escalatedItem.getVoteThreshold(),
                thresholdMet);
        eventService.publish(RetroEvent.escalationVoteUpdated(retroId, participant.getParticipantId().toString(), payload));

        return new EscalationVoteResultDto(
                escalationId,
                voteCount,
                escalatedItem.getVoteThreshold(),
                thresholdMet,
                voted);
    }

    @Transactional(readOnly = true)
    public List<EscalatedItemDto> getEscalations(UUID retroId, HttpServletRequest request) {
        participantService.getParticipantForSession(request, retroId);

        return escalatedItemRepository.findByRetroSession_IdOrderByCreatedAtAsc(retroId).stream()
                .map(this::toEscalatedItemDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EscalatedItemDto> getManagerEscalations(Authentication authentication) {
        List<UUID> managedTeamIds = getManagedTeamIds(authentication);
        if (managedTeamIds.isEmpty()) {
            return List.of();
        }

        return escalatedItemRepository.findByTeam_IdInOrderByCreatedAtAsc(managedTeamIds).stream()
                .map(this::toEscalatedItemDto)
                .filter(EscalatedItemDto::thresholdMet)
                .toList();
    }

    @Transactional(readOnly = true)
    public EscalatedItemDto getManagerEscalation(UUID escalationId, Authentication authentication) {
        List<UUID> managedTeamIds = getManagedTeamIds(authentication);
        if (managedTeamIds.isEmpty()) {
            throw new ResourceNotFoundException("Escalated item not found");
        }

        EscalatedItem escalatedItem = escalatedItemRepository.findByIdAndTeam_IdIn(escalationId, managedTeamIds)
                .orElseThrow(() -> new ResourceNotFoundException("Escalated item not found"));

        EscalatedItemDto dto = toEscalatedItemDto(escalatedItem);
        if (!dto.thresholdMet()) {
            throw new ResourceNotFoundException("Escalated item not found");
        }

        return dto;
    }

    private EscalatedItemDto toEscalatedItemDto(EscalatedItem escalatedItem) {
        long voteCount = escalatedItemVoteRepository.countByEscalatedItemId(escalatedItem.getId());
        return EscalatedItemDto.from(escalatedItem, voteCount, isThresholdMet(escalatedItem, voteCount));
    }

    private boolean isThresholdMet(EscalatedItem escalatedItem, long voteCount) {
        if (voteCount >= escalatedItem.getVoteThreshold()) {
            return true;
        }

        if (voteCount != escalatedItem.getVoteThreshold() - 1) {
            return false;
        }

        List<Participant> participants = participantService.getSessionParticipants(escalatedItem.getRetroSession().getId());
        if (participants.size() % 2 != 0) {
            return false;
        }

        return participants.stream()
                .filter(participant -> participant.getRole() == ParticipantRole.FACILITATOR)
                .findFirst()
                .map(Participant::getParticipantId)
                .flatMap(facilitatorId -> escalatedItemVoteRepository.findByEscalatedItemIdAndParticipantId(
                        escalatedItem.getId(),
                        facilitatorId))
                .isPresent();
    }

    private List<UUID> getManagedTeamIds(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return List.of();
        }

        UUID userId = authService.toOidcUserId(authentication.getName());
        return teamMemberRepository.findByUserIdAndRole(userId, TeamRole.MANAGER).stream()
                .map(teamMember -> teamMember.getTeam().getId())
                .toList();
    }
}
