package direct.reflect.facilitator.facilitation.session;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RetroSessionRepository extends JpaRepository<RetroSession, UUID> {

  Optional<RetroSession> findFirstByTeamIdAndPhaseAndIdNotAndCreatedAtBeforeOrderByFinishedAtDescCreatedAtDesc(
      UUID teamId,
      RetroPhase phase,
      UUID currentSessionId,
      LocalDateTime currentSessionCreatedAt);
}
