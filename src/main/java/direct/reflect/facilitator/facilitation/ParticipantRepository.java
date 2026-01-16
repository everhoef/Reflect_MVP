package direct.reflect.facilitator.facilitation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ParticipantRepository extends JpaRepository<Participant, ParticipantId> {
    List<Participant> findBySession_Id(UUID sessionId);

    /**
     * Find participants in a specific session filtered by status.
     * Use this to get ACTIVE participants (excludes LEFT/INACTIVE).
     */
    List<Participant> findBySession_IdAndStatus(UUID sessionId, ParticipantStatus status);

    Optional<Participant> findByUsername(String username);

    List<Participant> findByParticipantId(UUID participantId);

    /**
     * Find participant records for a specific user filtered by status.
     * Use this for session history queries (e.g., all ACTIVE sessions for user).
     */
    List<Participant> findByParticipantIdAndStatus(UUID participantId, ParticipantStatus status);

    @Query("SELECT p FROM Participant p JOIN FETCH p.session WHERE p.participantId = :participantId")
    List<Participant> findByParticipantIdWithSession(@Param("participantId") UUID participantId);

    /**
     * Find participant records with session data eagerly loaded, filtered by status.
     */
    @Query("SELECT p FROM Participant p JOIN FETCH p.session WHERE p.participantId = :participantId AND p.status = :status")
    List<Participant> findByParticipantIdAndStatusWithSession(@Param("participantId") UUID participantId, @Param("status") ParticipantStatus status);

    Optional<Participant> findByParticipantIdAndSession_Id(UUID participantId, UUID sessionId);

    Optional<Participant> findBySessionAndRoleAndParticipantId(
            RetroSession session, ParticipantRole role, UUID participantId);

    boolean existsBySessionAndParticipantId(RetroSession session, UUID participantId);
}
