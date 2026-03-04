package direct.reflect.facilitator.facilitation.response;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import direct.reflect.facilitator.facilitation.RetroSession;
import direct.reflect.facilitator.facilitation.Participant;
import direct.reflect.facilitator.configurator.RetroStep;
import direct.reflect.facilitator.configurator.RetroStage;
import direct.reflect.facilitator.configurator.ComponentType;

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
     * Count distinct participants who have responded to a step.
     * Used for ALL_RESPONDED advancement trigger to check if everyone has participated.
     */
    @Query("SELECT COUNT(DISTINCT r.participant) FROM ParticipantResponse r WHERE r.participant.session = :session AND r.retroStep = :step")
    Long countDistinctParticipantsBySessionAndStep(@Param("session") RetroSession session, @Param("step") RetroStep step);

    /**
     * Find all responses for a specific category (for MULTI_COLUMN_BOARD component).
     * Uses JSON query to filter by category field in responseData.
     */
    @Query(value = "SELECT * FROM participant_responses r WHERE r.session_id = :#{#session.id} AND r.retro_step_id = :#{#step.id} AND r.response_data->>'category' = :category ORDER BY r.display_order ASC, r.submitted_at ASC", nativeQuery = true)
    List<ParticipantResponse> findCategoricalByCategory(
        @Param("session") RetroSession session,
        @Param("step") RetroStep step,
        @Param("category") String category);

     /**
      * Find all responses for a specific component type within a stage.
      * Used by display components (e.g., HISTOGRAM_CHART) to retrieve responses
      * from input components (e.g., RATING_SCALE) within the same stage.
      * Uses explicit JOIN to navigate the RetroStep relationship.
      */
     @Query("SELECT r FROM ParticipantResponse r " +
            "JOIN r.retroStep step " +
            "WHERE r.participant.session = :session " +
            "AND step.retroStage = :stage " +
            "AND step.componentType = :componentType " +
            "ORDER BY r.displayOrder ASC, r.submittedAt ASC")
     List<ParticipantResponse> findBySessionAndStageAndComponentType(
         @Param("session") RetroSession session,
         @Param("stage") RetroStage stage,
         @Param("componentType") ComponentType componentType);

     /**
      * Count responses submitted by a specific participant for a specific step.
      * Used to enforce 10-input limit per step for MULTI_COLUMN_BOARD.
      */
     @Query("SELECT COUNT(r) FROM ParticipantResponse r WHERE r.participant = :participant AND r.participant.session = :session AND r.retroStep = :step")
     Long countByParticipantSessionAndStep(@Param("participant") Participant participant, @Param("session") RetroSession session, @Param("step") RetroStep step);

    /**
     * Find all responses for a specific step that belong to a given cluster.
     * Used by the clustering feature to retrieve all responses within a cluster.
     */
    List<ParticipantResponse> findByRetroStepIdAndClusterId(Long stepId, UUID clusterId);

    @Query("SELECT r FROM ParticipantResponse r WHERE r.participant.session.id = :retroId AND r.clusterId = :clusterId")
    List<ParticipantResponse> findByRetroIdAndClusterId(@Param("retroId") UUID retroId, @Param("clusterId") UUID clusterId);

    /**
     * Find all responses for a specific step that have not yet been assigned to any cluster.
     * Used by the clustering feature to find unclustered responses.
     */
    List<ParticipantResponse> findByRetroStepIdAndClusterIdIsNull(Long stepId);

    List<ParticipantResponse> findByRetroStepIdAndClusterIdIsNotNull(Long stepId);

    @Query("SELECT r FROM ParticipantResponse r WHERE r.participant.session.id = :retroId AND r.clusterId IS NULL ORDER BY r.displayOrder ASC, r.submittedAt ASC")
    List<ParticipantResponse> findByRetroIdAndClusterIdIsNull(@Param("retroId") UUID retroId);

    @Query("SELECT r FROM ParticipantResponse r WHERE r.participant.session.id = :retroId AND r.clusterId IS NOT NULL ORDER BY r.displayOrder ASC, r.submittedAt ASC")
    List<ParticipantResponse> findByRetroIdAndClusterIdIsNotNull(@Param("retroId") UUID retroId);
}
