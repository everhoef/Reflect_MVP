package direct.reflect.facilitator.facilitation.actions;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ActionItemRepository extends JpaRepository<ActionItem, UUID> {

    @Query("SELECT a FROM ActionItem a WHERE a.retroSession.id = :retroSessionId ORDER BY a.createdAt ASC")
    List<ActionItem> findByRetroSessionId(@Param("retroSessionId") UUID retroSessionId);

    @Query("SELECT a FROM ActionItem a WHERE a.retroSession.id = :retroSessionId AND a.status = :status ORDER BY a.createdAt ASC")
    List<ActionItem> findByRetroSessionIdAndStatus(
            @Param("retroSessionId") UUID retroSessionId,
            @Param("status") ActionItemStatus status);
}
