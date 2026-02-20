package direct.reflect.facilitator.facilitation.actionitem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ActionItemRepository extends JpaRepository<ActionItem, UUID> {

    List<ActionItem> findByRetroSessionId(UUID retroSessionId);

    List<ActionItem> findByAssignedToParticipantId(UUID participantId);
}
