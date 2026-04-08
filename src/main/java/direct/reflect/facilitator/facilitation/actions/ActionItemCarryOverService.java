package direct.reflect.facilitator.facilitation.actions;

import direct.reflect.facilitator.common.exception.RetroSessionNotFoundException;
import direct.reflect.facilitator.facilitation.RetroPhase;
import direct.reflect.facilitator.facilitation.RetroSession;
import direct.reflect.facilitator.facilitation.RetroSessionRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActionItemCarryOverService {

  private final RetroSessionRepository sessionRepository;
  private final ActionItemRepository actionItemRepository;

  public List<ActionItemDto> getPreviousOpenActions(UUID retroId) {
    RetroSession currentSession = sessionRepository.findById(retroId)
        .orElseThrow(() -> new RetroSessionNotFoundException(retroId));

    if (currentSession.getTeam() == null || currentSession.getCreatedAt() == null) {
      return List.of();
    }

    return sessionRepository
        .findFirstByTeam_IdAndPhaseAndIdNotAndCreatedAtBeforeOrderByFinishedAtDescCreatedAtDesc(
            currentSession.getTeam().getId(),
            RetroPhase.COMPLETED,
            currentSession.getId(),
            currentSession.getCreatedAt())
        .map(previousSession -> actionItemRepository
            .findByRetroSessionIdAndStatus(previousSession.getId(), ActionItemStatus.OPEN)
            .stream()
            .map(ActionItemDto::from)
            .toList())
        .orElseGet(List::of);
  }
}
