package direct.reflect.facilitator.facilitation.participant;

/**
 * Status of a participant's involvement in a specific retrospective session.
 *
 * Design rationale:
 * - Participants are session-scoped (composite key: participantId + sessionId)
 * - Same user can have multiple Participant records (one per session)
 * - Status tracks participation lifecycle without deleting data
 * - Responses remain linked to participant even after they leave
 * - SSE disconnections do NOT change status (auto-reconnect expected)
 */
public enum ParticipantStatus {
    /**
     * User is currently active in this session.
     * Can submit responses, see updates, interact with retro.
     */
    ACTIVE,

    /**
     * User has left this session (e.g., created a new session elsewhere).
     * Responses remain in database for audit/analytics.
     * User no longer appears in participant list.
     */
    LEFT,

    /**
     * Session has ended or participant was removed by facilitator.
     * Responses remain in database for reporting.
     */
    INACTIVE
}
