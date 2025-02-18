package direct.reflect.facilitator.domain.enums;

public enum EventType {
    // Retro lifecycle (planned)
    RETRO_CREATED,       // When template is instantiated
    RETRO_STARTED,       // When facilitator starts retro
    RETRO_ENDED,         // When last phase completes
    
    // Phase management (planned)
    PHASE_TRANSITION,    // Moving to next phase
    STEP_TRANSITION,     // Moving to next step within phase
    
    // Participation events (reactive)
    PARTICIPANT_JOINED,  // User enters lobby
    PARTICIPANT_LEFT,    // User leaves retro
    CLIENT_CONNECTED,    // WebSocket/SSE connection
    CLIENT_DISCONNECTED, // WebSocket/SSE disconnection
    
    // Content events
    PHASE_STARTED,       // Facilitator starts phase
    PHASE_ENDED,         // Facilitator ends phase
    PHASE_TIMED_OUT,     // Phase exceeds time limit
    CARD_CREATED,
    CARD_UPDATED,
    CARD_DELETED,
    CARD_MOVED,
    CARD_GROUPED,
    
    // Interaction events
    VOTE_ADDED,
    VOTE_REMOVED,
    COMMENT_ADDED,
    COMMENT_UPDATED,
    COMMENT_DELETED,
    
    // Display events (planned)
    DISPLAY_UPDATE,      // Update UI for all
    TIMER_UPDATE,        // Timer events
    
    // VA events (planned)
    VA_INSTRUCTION,      // VA provides guidance
    VA_SUMMARY          // VA summarizes phase

}