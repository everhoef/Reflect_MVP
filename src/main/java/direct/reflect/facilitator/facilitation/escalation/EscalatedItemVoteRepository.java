package direct.reflect.facilitator.facilitation.escalation;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EscalatedItemVoteRepository extends JpaRepository<EscalatedItemVote, EscalatedItemVoteId> {

    @Query("SELECT COUNT(v) FROM EscalatedItemVote v WHERE v.escalatedItem.id = :escalatedItemId")
    long countByEscalatedItemId(@Param("escalatedItemId") UUID escalatedItemId);

    @Query("""
            SELECT v.escalatedItem.id, COUNT(v)
            FROM EscalatedItemVote v
            WHERE v.escalatedItem.id IN :escalatedItemIds
            GROUP BY v.escalatedItem.id
            """)
    List<Object[]> countByEscalatedItemIdIn(@Param("escalatedItemIds") Collection<UUID> escalatedItemIds);

    @Query("SELECT v FROM EscalatedItemVote v WHERE v.escalatedItem.id = :escalatedItemId AND v.id.participantId = :participantId")
    Optional<EscalatedItemVote> findByEscalatedItemIdAndParticipantId(
            @Param("escalatedItemId") UUID escalatedItemId,
            @Param("participantId") UUID participantId);
}
