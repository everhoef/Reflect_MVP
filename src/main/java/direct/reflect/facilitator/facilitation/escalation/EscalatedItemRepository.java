package direct.reflect.facilitator.facilitation.escalation;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EscalatedItemRepository extends JpaRepository<EscalatedItem, UUID> {

    Optional<EscalatedItem> findByIdAndRetroSession_Id(UUID id, UUID retroSessionId);

    List<EscalatedItem> findByRetroSession_IdOrderByCreatedAtAsc(UUID retroSessionId);

    List<EscalatedItem> findByTeamIdInOrderByCreatedAtAsc(Collection<UUID> teamIds);

    Optional<EscalatedItem> findByIdAndTeamIdIn(UUID id, Collection<UUID> teamIds);
}
