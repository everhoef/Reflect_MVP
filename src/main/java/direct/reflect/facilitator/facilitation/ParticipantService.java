package direct.reflect.facilitator.facilitation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import direct.reflect.facilitator.common.exception.ParticipantNotFoundException;
import direct.reflect.facilitator.auth.AuthService;

/**
 * Service responsible for managing {@link Participant} entities.
 * 
 * Refactored for OIDC + Anonymous Guest hybrid authentication:
 * - Uses AuthService for clean identity extraction
 * - Supports both OIDC users and anonymous guests uniformly
 * - Focuses on business logic rather than authentication concerns
 * 
 * This includes creating participants, assigning roles (FACILITATOR/PARTICIPANT),
 * associating them with RetroSessions, and handling participant lifecycle.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ParticipantService {
    private final ParticipantRepository participantRepository;
    private final RetroSessionService retroSessionService;
    private final AuthService authHelper;


    /**
     * Creates a new retro session with the current user as facilitator.
     * Works for both OIDC users and anonymous guests.
     */
    @Transactional
    public Participant createAndAssignFacilitatorForSession(String sessionName, HttpServletRequest request) {
        
        // For session creation, we automatically terminate old sessions
        UUID participantId = authHelper.getParticipantId(request);
        List<Participant> activeSessions = getActiveSessionsForParticipant(participantId);
        if (!activeSessions.isEmpty()) {
            log.info("Participant {} creating new session - terminating {} existing session(s)", 
                participantId, activeSessions.size());
                
            for (Participant activeParticipant : activeSessions) {
                removeParticipantFromSession(activeParticipant);
            }
            
            log.info("Terminated {} old session(s) for new session creation", activeSessions.size());
        }
        
        // Create session and add user as facilitator
        RetroSession newSession = retroSessionService.createNewSession(sessionName);
        return createParticipantForSession(newSession, ParticipantRole.FACILITATOR, request);
    }
    
    


    /**
     * Adds a user to an existing retro session.
     */
    @Transactional
    public Participant addParticipantToSession(HttpServletRequest request, RetroSession session, ParticipantRole role) {
        if (role == null) {
            throw new IllegalArgumentException("Participant role cannot be null");
        }
        if (session == null || session.getId() == null) {
            throw new IllegalArgumentException("Session and session ID cannot be null");
        }

        // Get current user identity (throws if session not properly initialized)
        UUID participantId = authHelper.getParticipantId(request);
        authHelper.getDisplayName(request); // Validate display name is set
        
        checkForActiveSession(participantId, session.getId());
        Participant participant = createParticipantForSession(session, role, request);
        
        // Notify RetroSessionService to handle the event publishing
        retroSessionService.onParticipantJoining(participant);
        
        return participant;
    }

    /**
     * Gets current user's participant record for a specific session.
     * Works with both OIDC users and anonymous guests.
     */
    public Participant getParticipantForSession(HttpServletRequest request, UUID sessionId) {
        UUID participantId = authHelper.getParticipantId(request);
        log.debug("Looking up participant: participantId={}, sessionId={}", participantId, sessionId);

        // Check if participant exists in database
        Optional<Participant> result = participantRepository.findByParticipantIdAndSession_Id(participantId, sessionId);

        if (result.isEmpty()) {
            // Log context for debugging session issues
            HttpSession httpSession = request.getSession(false);
            String authType = httpSession != null ? (String) httpSession.getAttribute("authType") : "null";
            String displayName = httpSession != null ? (String) httpSession.getAttribute("userDisplayName") : "null";
            String httpSessionId = httpSession != null ? httpSession.getId() : "null";

            log.warn("Participant NOT FOUND in database: participantId={}, sessionId={}", participantId, sessionId);
            log.debug("Session context: authType={}, displayName={}, httpSessionId={}", authType, displayName, httpSessionId);

            // List all participants in this session for debugging
            List<Participant> allParticipants = participantRepository.findBySession_Id(sessionId);
            log.debug("Total participants in session {}: {}", sessionId, allParticipants.size());
            allParticipants.forEach(p ->
                log.debug("  - Participant: id={}, displayName={}, role={}",
                    p.getParticipantId(), p.getDisplayName(), p.getRole()));

            throw new ParticipantNotFoundException(
                "Not authorized for session: " + sessionId + " (participant: " + participantId + ")");
        }

        log.debug("Participant found: displayName={}, role={}", result.get().getDisplayName(), result.get().getRole());
        return result.get();
    }
    
    /**
     * Gets all participants in a session.
     */
    public List<Participant> getSessionParticipants(UUID sessionId) {
        return participantRepository.findBySession_Id(sessionId);
    }

    /**
     * Checks if the current user is the facilitator of a session.
     */
    public boolean isFacilitator(HttpServletRequest request, UUID sessionId) {
        try {
            Participant participant = getParticipantForSession(request, sessionId);
            return participant.getRole() == ParticipantRole.FACILITATOR;
        } catch (ParticipantNotFoundException e) {
            return false;
        }
    }

    /**
     * Checks if the current user is participating in a session.
     */
    public boolean isParticipating(HttpServletRequest request, UUID sessionId) {
        try {
            getParticipantForSession(request, sessionId);
            return true;
        } catch (ParticipantNotFoundException e) {
            return false;
        }
    }

    /**
     * Updates the last seen timestamp for a user in a session.
     */
    @Transactional
    public void updateLastSeen(HttpServletRequest request, UUID sessionId) {
        Participant participant = getParticipantForSession(request, sessionId);
        participant.setLastSeen(LocalDateTime.now());
        participantRepository.save(participant);
        log.debug("Updated lastSeen for participant '{}' (ID: {}) in session {}", 
            participant.getDisplayName(), participant.getParticipantId(), sessionId);
    }


    /**
     * Checks if a participant has any active sessions and handles session switching.
     * For same session: allows rejoin
     * For different session: terminates old session and allows new one
     */
    private void checkForActiveSession(UUID participantId, UUID targetSessionId) {
        List<Participant> activeSessions = getActiveSessionsForParticipant(participantId);
        
        if (!activeSessions.isEmpty()) {
            // Check if they're trying to join a session they're already in
            boolean alreadyInTargetSession = activeSessions.stream()
                .anyMatch(p -> p.getSession().getId().equals(targetSessionId));
                
            if (alreadyInTargetSession) {
                log.info("Participant {} is already in target session {} - allowing rejoin", 
                    participantId, targetSessionId);
                return; // Allow rejoining the same session
            }
            
            // They're switching to a different session - terminate old sessions
            log.info("Participant {} switching from {} active session(s) to new session {}", 
                participantId, activeSessions.size(), targetSessionId);
                
            for (Participant activeParticipant : activeSessions) {
                removeParticipantFromSession(activeParticipant);
            }
            
            log.info("Successfully terminated {} old session(s) for participant {}", 
                activeSessions.size(), participantId);
        }
    }
    
    /**
     * Gets all active sessions for a participant.
     */
    public List<Participant> getActiveSessionsForParticipant(UUID participantId) {
        log.debug("Checking for active sessions for participant: {}", participantId);
            
        List<Participant> existing = participantRepository.findByParticipantIdWithSession(participantId);
        
        log.debug("Found {} existing participations for participant {}", existing.size(), participantId);
        
        List<Participant> activeSessions = existing.stream()
            .filter(p -> {
                boolean isFinished = p.getSession().isFinished();
                log.debug("Session {} is finished: {}", p.getSession().getId(), isFinished);
                return !isFinished;
            })
            .toList();
        
        log.debug("Participant {} has {} active sessions", participantId, activeSessions.size());
        return activeSessions;
    }
    
    /**
     * Leaves all active sessions for the current user.
     */
    @Transactional
    public void leaveAllActiveSessions(HttpServletRequest request) {
        UUID participantId = authHelper.getParticipantId(request);
        List<Participant> activeSessions = getActiveSessionsForParticipant(participantId);
        
        for (Participant activeParticipant : activeSessions) {
            removeParticipantFromSession(activeParticipant);
        }
        
        // Clear session attributes since we're leaving all sessions
        if (request.getSession(false) != null) {
            request.getSession().removeAttribute("retroId");
            request.getSession().removeAttribute("participantRole");
            request.getSession().removeAttribute("participantId");
            log.debug("Cleared session attributes after leaving all active sessions");
        }
        
        log.info("Successfully removed participant {} from {} active sessions", 
            participantId, activeSessions.size());
    }

    /**
     * Creates a participant for an existing session and sets session attributes.
     * Prevents duplicate participants by checking if this participantId already exists in the session.
     */
    private Participant createParticipantForSession(RetroSession session, ParticipantRole role, HttpServletRequest request) {
        // Get user identity from AuthService
        UUID participantId = authHelper.getParticipantId(request);
        String username = authHelper.getUsername(request); // null for guests
        String displayName = authHelper.getDisplayName(request);
        
        log.debug("createParticipantForSession called with role: {} for participant: {} in session: {}", 
            role, participantId, session.getId());
        
        // Check if this participant already exists in this session
        ParticipantId pk = new ParticipantId(participantId, session.getId());
        var existingParticipant = participantRepository.findById(pk);
        
        if (existingParticipant.isPresent()) {
            log.debug("Participant {} already exists in session {}. Current role: {}, requested role: {}", 
                participantId, session.getId(), existingParticipant.get().getRole(), role);
            
            // Update existing participant
            Participant existing = existingParticipant.get();
            existing.setDisplayName(displayName);
            existing.setLastSeen(LocalDateTime.now());
            existing.setRole(role); // Allow role updates
            
            log.debug("About to save updated participant with role: {}", existing.getRole());
            Participant updatedParticipant = participantRepository.save(existing);
            log.debug("Saved updated participant. Role after save: {}", updatedParticipant.getRole());
            
            // Update session attributes
            if (request.getSession(false) != null) {
                request.getSession().setAttribute("retroId", session.getId());
                request.getSession().setAttribute("participantRole", role.name());
                request.getSession().setAttribute("participantId", participantId);
                log.debug("Updated session attributes: retroId={}, role={}, participantId={}", 
                    session.getId(), role.name(), participantId);
            }
            
            return updatedParticipant;
        }
        
        log.debug("Creating new participant with role: {} for participant: {} in session: {}", 
            role, participantId, session.getId());
        
        // Create new participant
        Participant participant = new Participant();
        participant.setParticipantId(participantId);
        participant.setUsername(username); // null for guests
        participant.setDisplayName(displayName);
        participant.setSession(session);
        participant.setRole(role);
        participant.setLastSeen(LocalDateTime.now());
        
        log.debug("About to save new participant with role: {}", participant.getRole());
        Participant savedParticipant = participantRepository.save(participant);
        log.debug("Saved new participant. Role after save: {}", savedParticipant.getRole());
        
        // Set session attributes for participant tracking
        if (request.getSession(false) != null) {
            request.getSession().setAttribute("retroId", session.getId());
            request.getSession().setAttribute("participantRole", role.name());
            request.getSession().setAttribute("participantId", participantId);
            log.debug("Set session attributes: retroId={}, role={}, participantId={}", 
                session.getId(), role.name(), participantId);
        }
        
        return savedParticipant;
    }
    
    /**
     * Removes a participant from their session with proper event handling.
     */
    @Transactional
    public void removeParticipantFromSession(Participant participant) {
        log.info("=== REMOVING PARTICIPANT === {} from session {} ({})",
            participant.getParticipantId(),
            participant.getSession().getId(),
            participant.getSession().getName());

        // Notify RetroSessionService to handle the event publishing
        log.info("About to call retroSessionService.onParticipantLeaving for participant: {}", participant.getDisplayName());
        retroSessionService.onParticipantLeaving(participant);

        // Remove the participant from the database
        log.info("About to delete participant {} from database", participant.getDisplayName());
        participantRepository.delete(participant);
        log.info("Successfully deleted participant {} from database", participant.getDisplayName());
    }

    // ========== AUTHORIZATION METHODS (for Spring Security @PreAuthorize) ==========

    /**
     * Check if current user can access a retrospective session.
     * Used in @PreAuthorize("@participantService.canAccessRetro(#retroId)")
     *
     * Note: This method uses RequestContextHolder to extract the request since
     * @PreAuthorize expressions execute before controller methods run.
     * All other methods in this service use explicit HttpServletRequest parameters.
     */
    public boolean canAccessRetro(UUID retroId) {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            log.warn("No HTTP request context available for authorization check");
            return false;
        }

        try {
            return isParticipating(request, retroId);
        } catch (Exception e) {
            log.debug("Access denied to retro {}: {}", retroId, e.getMessage());
            return false;
        }
    }

    /**
     * Check if current user is facilitator for a retrospective session.
     * Used in @PreAuthorize("@participantService.isFacilitator(#retroId)")
     *
     * Note: This is an overload for @PreAuthorize usage that extracts the request internally.
     * The standard isFacilitator(HttpServletRequest, UUID) method should be used elsewhere.
     */
    public boolean isFacilitator(UUID retroId) {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            log.warn("No HTTP request context available for authorization check");
            return false;
        }

        try {
            return isFacilitator(request, retroId);
        } catch (Exception e) {
            log.debug("Facilitator check failed for retro {}: {}", retroId, e.getMessage());
            return false;
        }
    }

    /**
     * Extract current HttpServletRequest from Spring's RequestContextHolder.
     *
     * IMPORTANT: This method should ONLY be used by authorization methods for @PreAuthorize.
     * All other methods should use explicit HttpServletRequest parameters for consistency.
     */
    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

}
