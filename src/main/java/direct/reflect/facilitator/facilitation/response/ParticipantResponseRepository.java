package direct.reflect.facilitator.facilitation.response;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import direct.reflect.facilitator.facilitation.RetroSession;
import direct.reflect.facilitator.facilitation.Participant;
import direct.reflect.facilitator.configurator.RetroStep;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ParticipantResponseRepository extends JpaRepository<ParticipantResponse, UUID> {

    /**
     * Find all responses for a specific step in a session.
     */
    @Query("SELECT r FROM ParticipantResponse r WHERE r.participant.session = :session AND r.retroStep = :step ORDER BY r.displayOrder ASC, r.submittedAt ASC")
    List<ParticipantResponse> findBySessionAndRetroStep(@Param("session") RetroSession session, @Param("step") RetroStep step);

    /**
     * Find a participant's response for a specific step (for RATING pattern - one response per participant).
     */
    @Query("SELECT r FROM ParticipantResponse r WHERE r.participant.session = :session AND r.retroStep = :step AND r.participant = :participant")
    Optional<ParticipantResponse> findBySessionAndRetroStepAndParticipant(
        @Param("session") RetroSession session,
        @Param("step") RetroStep step,
        @Param("participant") Participant participant);

    /**
     * Find only visible responses for a step (for PRIVATE → PUBLIC reveal).
     */
    @Query("SELECT r FROM ParticipantResponse r WHERE r.participant.session = :session AND r.retroStep = :step AND r.isVisible = true ORDER BY r.displayOrder ASC, r.submittedAt ASC")
    List<ParticipantResponse> findVisibleBySessionAndRetroStep(@Param("session") RetroSession session, @Param("step") RetroStep step);

    /**
     * Count total responses for a step (for participation tracking).
     */
    @Query("SELECT COUNT(r) FROM ParticipantResponse r WHERE r.participant.session = :session AND r.retroStep = :step")
    Long countResponsesForStep(@Param("session") RetroSession session, @Param("step") RetroStep step);

    /**
     * Find all categorical responses for a specific category.
     */
    @Query("SELECT r FROM CategoricalResponse r WHERE r.participant.session = :session AND r.retroStep = :step AND r.category = :category ORDER BY r.displayOrder ASC, r.submittedAt ASC")
    List<CategoricalResponse> findCategoricalByCategory(
        @Param("session") RetroSession session,
        @Param("step") RetroStep step,
        @Param("category") String category);
}
