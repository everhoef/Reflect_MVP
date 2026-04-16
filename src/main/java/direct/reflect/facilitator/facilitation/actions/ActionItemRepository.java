package direct.reflect.facilitator.facilitation.actions;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ActionItemRepository extends JpaRepository<ActionItem, UUID> {

    List<ActionItem> findByRetroSessionIdOrderByCreatedAtAsc(UUID retroSessionId);

    List<ActionItem> findByRetroSessionIdAndStatusOrderByCreatedAtAsc(UUID retroSessionId, ActionItemStatus status);

    default List<ActionItem> findByRetroSessionId(UUID retroSessionId) {
        return findByRetroSessionIdOrderByCreatedAtAsc(retroSessionId);
    }

    default List<ActionItem> findByRetroSessionIdAndStatus(UUID retroSessionId, ActionItemStatus status) {
        return findByRetroSessionIdAndStatusOrderByCreatedAtAsc(retroSessionId, status);
    }
}
