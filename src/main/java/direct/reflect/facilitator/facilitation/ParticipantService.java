package direct.reflect.facilitator.facilitation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Cookie;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import direct.reflect.facilitator.configurator.RetroTemplate;
import direct.reflect.facilitator.common.exception.ParticipantNotFoundException;

/**
 * Service responsible for managing {@link Participant} entities.
 * This includes creating participants, assigning roles (like FACILITATOR),
 * associating them with {@link RetroSession}s, and handling participant identification
 * through cookies for anonymous users and Spring Security for authenticated users.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ParticipantService {
    private final ParticipantRepository participantRepository;
    private final RetroSessionService retroSessionService;

    public static final String PARTICIPANT_ID_COOKIE = "PARTICIPANT_ID"; // Made public for test access
    public static final int COOKIE_MAX_AGE = 60 * 60 * 24 * 30; // Made public for test access

    /**
     * Creates a new retro session with the current user as facilitator.
     * Works for both authenticated users (USER role) and guests (GUEST role).
     */
    @Transactional
    public Participant createAndAssignFacilitatorForSession(String sessionName, String displayName, HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new IllegalStateException("No authentication context found");
        }
        
        // Display name comes from the authentication context
        Participant profile = createParticipantFromAuthentication(auth, null, request);
        validateDisplayName(profile);
        checkForActiveSession(profile);
        return createNewSessionWithFacilitator(sessionName, profile, request);
    }
    
    /**
     * Gets the current user's participant profile from Spring Security context.
     * Works for both authenticated users (USER role) and guests (GUEST role).
     */
    public Participant getCurrentParticipant(HttpServletRequest request, String displayName) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new IllegalStateException("No authentication context found");
        }
        
        try {
            return createParticipantFromAuthentication(auth, null, request);
        } catch (Exception e) {
            log.warn("Error getting current participant: {}", e.getMessage());
            throw new IllegalStateException("Authentication context is required", e);
        }
    }
    
    /**
     * Creates a participant profile from Spring Security Authentication object.
     * Display name is always taken from the authentication context.
     * For guests, participant ID comes from cookies to ensure consistency.
     */
    private Participant createParticipantFromAuthentication(Authentication auth, String displayName, HttpServletRequest request) {
        Participant profile = new Participant();
        
        // Check if this is a guest user based on role
        boolean isGuest = auth.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("ROLE_GUEST"));
        
        String username = auth.getName();
        
        if (isGuest) {
            // Guest user - get participant ID from cookie for consistency
            UUID participantId = extractParticipantId(auth, request);
            profile.setParticipantId(participantId);
            profile.setUsername(null); // Guests don't have usernames
            profile.setDisplayName(username != null ? username : "Guest User"); // For guests, username = displayName
            log.debug("Created GUEST participant profile: ID={}, displayName={}", 
                profile.getParticipantId(), profile.getDisplayName());
        } else {
            // Regular authenticated user - use consistent ID based on username
            profile.setParticipantId(generateUserBasedParticipantId(username));
            profile.setUsername(username);
            profile.setDisplayName(displayName != null ? displayName : username);
            log.debug("Created USER participant profile: ID={}, username={}, displayName={}", 
                profile.getParticipantId(), profile.getUsername(), profile.getDisplayName());
        }
        
        return profile;
    }

    /**
     * Generates a consistent participant ID for authenticated users based on username.
     */
    private UUID generateUserBasedParticipantId(String username) {
        // Generate a consistent UUID based on username
        // This ensures the same user always gets the same participant ID
        return UUID.nameUUIDFromBytes(("user:" + username).getBytes());
    }

    /**
     * Adds a user to an existing retro session.
     */
    @Transactional
    public Participant addParticipantToSession(HttpServletRequest request, RetroSession session, String displayName, ParticipantRole role) {
        if (role == null) {
            throw new IllegalArgumentException("Participant role cannot be null");
        }
        if (session == null || session.getId() == null) {
            throw new IllegalArgumentException("Session and session ID cannot be null");
        }

        Participant profile = getCurrentParticipant(request, displayName);
        validateDisplayName(profile);
        checkForActiveSession(profile);
        return createParticipantForSession(session, profile, role, request);
    }

    /**
     * Gets a participant in a specific session using session attributes, throwing an exception if not authorized.
     * Works with both authenticated users and anonymous participants via Spring Security.
     */
    public Participant getParticipantForSession(HttpServletRequest request, UUID sessionId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new IllegalStateException("No authentication context found");
        }
        
        // Use ONLY session attributes for security
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new ParticipantNotFoundException("No active session found");
        }
        
        UUID sessionRetroId = (UUID) session.getAttribute("retroId");
        UUID sessionParticipantId = (UUID) session.getAttribute("participantId");
        
        if (sessionRetroId == null || !sessionRetroId.equals(sessionId)) {
            throw new ParticipantNotFoundException("Not authorized for session: " + sessionId);
        }
        
        if (sessionParticipantId == null) {
            throw new ParticipantNotFoundException("No participant ID in session");
        }
        
        ParticipantId pk = new ParticipantId(sessionParticipantId, sessionId);
        return participantRepository.findById(pk)
            .orElseThrow(() -> new ParticipantNotFoundException(
                "Participant not found for session: " + sessionId));
    }
    
    /**
     * Checks if the current user has facilitator role for the given session using session attributes.
     */
    public boolean isFacilitatorForSession(HttpServletRequest request, UUID sessionId) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        
        UUID sessionRetroId = (UUID) session.getAttribute("retroId");
        String participantRole = (String) session.getAttribute("participantRole");
        
        return sessionRetroId != null && 
               sessionRetroId.equals(sessionId) && 
               "FACILITATOR".equals(participantRole);
    }

    /**
     * Extracts participant ID from Spring Security Authentication and cookies.
     */
    private UUID extractParticipantId(Authentication auth, HttpServletRequest request) {
        // Check if this is a guest user based on role
        boolean isGuest = auth.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("ROLE_GUEST"));
            
        if (isGuest) {
            // For guests, get the participant ID from the cookie
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if (PARTICIPANT_ID_COOKIE.equals(cookie.getName())) {
                        try {
                            return UUID.fromString(cookie.getValue());
                        } catch (IllegalArgumentException e) {
                            log.warn("Invalid participant ID in cookie: {}", cookie.getValue());
                            // Fallback to generate new UUID if cookie is corrupted
                            return UUID.randomUUID();
                        }
                    }
                }
            }
            
            log.warn("No participant ID cookie found for guest user, generating new UUID");
            return UUID.randomUUID();
        } else {
            // For authenticated users, generate consistent ID from username
            return generateUserBasedParticipantId(auth.getName());
        }
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
     * Validates that a participant has a non-blank display name.
     */
    private void validateDisplayName(Participant profile) {
        if (profile.getDisplayName() == null || profile.getDisplayName().isBlank()) {
            throw new IllegalArgumentException("Display name is required.");
        }
    }

    /**
     * Checks if a participant has any active sessions.
     */
    private void checkForActiveSession(Participant profile) {
        log.debug("Checking for active sessions for participant: {} (username: {})", 
            profile.getParticipantId(), profile.getUsername());
            
        List<Participant> existing = participantRepository.findByParticipantIdWithSession(profile.getParticipantId());
        
        log.debug("Found {} existing participations for participant {}", 
            existing.size(), profile.getParticipantId());
        
        boolean hasActive = existing.stream().anyMatch(p -> {
            boolean isFinished = p.getSession().isFinished();
            log.debug("Session {} is finished: {}", p.getSession().getId(), isFinished);
            return !isFinished;
        });
        
        log.debug("Participant {} has active sessions: {}", profile.getParticipantId(), hasActive);
        
        if (hasActive) {
            throw new IllegalStateException("Participant is already in an active session.");
        }
    }

    /**
     * Creates a new session with the given participant as facilitator.
     */
    private Participant createNewSessionWithFacilitator(String sessionName, Participant profile, HttpServletRequest request) {
        RetroTemplate template = retroSessionService.getDefaultTemplate();
        RetroSession newSession = retroSessionService.createNewSession(sessionName, template);
        return createParticipantForSession(newSession, profile, ParticipantRole.FACILITATOR, request);
    }

    /**
     * Creates a participant for an existing session and sets session attributes.
     */
    private Participant createParticipantForSession(RetroSession session, Participant profile, ParticipantRole role, HttpServletRequest request) {
        Participant participant = new Participant();
        participant.setParticipantId(profile.getParticipantId());
        participant.setUsername(profile.getUsername());
        participant.setDisplayName(profile.getDisplayName());
        participant.setSession(session);
        participant.setRole(role);
        participant.setLastSeen(LocalDateTime.now());
        
        Participant savedParticipant = participantRepository.save(participant);
        
        // Set session attributes for Spring Security session management
        if (request.getSession(false) != null) {
            request.getSession().setAttribute("retroId", session.getId());
            request.getSession().setAttribute("participantRole", role.name());
            request.getSession().setAttribute("participantId", profile.getParticipantId());
            log.debug("Set session attributes: retroId={}, role={}, participantId={}", 
                session.getId(), role.name(), profile.getParticipantId());
        }
        
        return savedParticipant;
    }
    
    /**
     * Creates a cookie with participant ID for guest users.
     */
    public Cookie createParticipantCookie(UUID participantId) {
        Cookie cookie = new Cookie(PARTICIPANT_ID_COOKIE, participantId.toString());
        cookie.setMaxAge(COOKIE_MAX_AGE);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        return cookie;
    }
}
