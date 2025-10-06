package direct.reflect.facilitator.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.UUID;

/**
 * Authentication & Authorization Service for OIDC + Guest hybrid model.
 *
 * Handles two concerns:
 * 1. Authentication: Extracts user identity from HTTP sessions for OIDC and Guest users
 * 2. Authorization: Checks participant-level permissions (session membership, facilitator role)
 *
 * Used by:
 * - Controllers for identity extraction
 * - Spring Security @PreAuthorize expressions (bean name: "authService")
 */
@Component("authService")
@Slf4j
public class AuthService {

    // Lazy-injected to avoid circular dependency
    private direct.reflect.facilitator.facilitation.ParticipantService participantService;

    @org.springframework.beans.factory.annotation.Autowired
    public void setParticipantService(direct.reflect.facilitator.facilitation.ParticipantService participantService) {
        this.participantService = participantService;
    }
    
    /**
     * Get participant ID (subject identifier) for current user.
     * Works for both OIDC users (deterministic UUID from username) and guests (random UUID).
     */
    public UUID getParticipantId(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        String authType = (String) session.getAttribute("authType");
        
        if ("OIDC".equals(authType)) {
            String username = (String) session.getAttribute("authenticatedUser");
            if (username == null) {
                throw new IllegalStateException("OIDC session missing authenticatedUser");
            }
            return generateUserBasedId(username);
        } else {
            return getOrCreateGuestId(session);
        }
    }
    
    /**
     * Get username for current user (null for guests).
     * Corresponds to OIDC 'preferred_username' claim.
     */
    public String getUsername(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && "OIDC".equals(session.getAttribute("authType"))) {
            return (String) session.getAttribute("authenticatedUser");
        }
        return null; // Guests don't have usernames
    }
    
    /**
     * Get display name for current user.
     * Corresponds to OIDC 'name' claim or guest-provided display name.
     */
    public String getDisplayName(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new IllegalStateException("No session found - user must be authenticated or initialized as guest");
        }
        
        String authType = (String) session.getAttribute("authType");
        if ("OIDC".equals(authType)) {
            String oidcDisplayName = (String) session.getAttribute("userDisplayName");
            if (oidcDisplayName == null) {
                throw new IllegalStateException("OIDC session missing userDisplayName");
            }
            return oidcDisplayName;
        } else {
            String guestDisplayName = (String) session.getAttribute("userDisplayName");
            if (guestDisplayName == null) {
                throw new IllegalStateException("Guest session missing userDisplayName - call initializeGuestSession first");
            }
            return guestDisplayName;
        }
    }
    
    /**
     * Check if current user is OIDC authenticated.
     */
    public boolean isOidcUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && "OIDC".equals(session.getAttribute("authType"));
    }
    
    /**
     * Initialize guest session using unified session structure.
     * Allows duplicate display names (like Zoom/Teams) - backend uses unique UUIDs.
     */
    public void initializeGuestSession(HttpServletRequest request, String displayName) {
        if (displayName == null || displayName.trim().isEmpty()) {
            throw new IllegalArgumentException("Guest display name cannot be empty");
        }
        
        HttpSession session = request.getSession(true);
        String trimmedDisplayName = displayName.trim();
        
        // Generate unique guest ID (acts as the "username" for guests)
        UUID guestId = UUID.randomUUID();
        String guestUsername = guestId.toString();
        
        // Use SAME session structure as OIDC for consistency
        session.setAttribute("authenticatedUser", guestUsername);     // Unique UUID
        session.setAttribute("userDisplayName", trimmedDisplayName);  // Can be duplicate
        session.setAttribute("authType", "GUEST");
        
        // Set up Spring Security authentication
        Authentication guestAuth = new UsernamePasswordAuthenticationToken(
            guestUsername, // Use unique guest ID as principal for Spring Security
            null, // No credentials for guests
            List.of(new SimpleGrantedAuthority("ROLE_GUEST"))
        );
        
        // Set security context
        SecurityContext context = new SecurityContextImpl(guestAuth);
        SecurityContextHolder.setContext(context);
        
        // Ensure context is properly saved to session - force explicit save
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        
        log.debug("Saved Spring Security context to session for guest: {}", guestUsername);
        
        log.info("Guest '{}' authenticated with unique ID: {}", trimmedDisplayName, guestUsername);
    }
    
    /**
     * Get guest participant ID from session.
     */
    private UUID getOrCreateGuestId(HttpSession session) {
        String guestUsername = (String) session.getAttribute("authenticatedUser");
        
        if (guestUsername == null) {
            throw new IllegalStateException("No guest session found - call initializeGuestSession first");
        }
        
        // The guest username is the UUID string, convert it back to UUID
        return UUID.fromString(guestUsername);
    }
    
    /**
     * Generate consistent UUID for OIDC users based on username.
     * Uses UUID v5 (namespace-based) for deterministic results.
     */
    private UUID generateUserBasedId(String username) {
        // Use standard namespace UUID for consistent hashing
        UUID namespace = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
        return UUID.nameUUIDFromBytes((namespace.toString() + username).getBytes());
    }

    // ========== AUTHORIZATION METHODS (for Spring Security @PreAuthorize) ==========

    /**
     * Check if the current user can access a specific retrospective session.
     * Used in @PreAuthorize("@authService.canAccessRetro(#retroId)")
     */
    public boolean canAccessRetro(UUID retroId) {
        try {
            HttpServletRequest request = getCurrentRequest();
            if (request == null) {
                log.warn("No HTTP request context available for security check");
                return false;
            }

            // Check if participant exists for this session
            direct.reflect.facilitator.facilitation.Participant participant =
                participantService.getParticipantForSession(request, retroId);
            return participant != null;

        } catch (Exception e) {
            log.debug("Access denied to retro {}: {}", retroId, e.getMessage());
            return false;
        }
    }

    /**
     * Check if the current user is a facilitator for a specific retrospective session.
     * Used in @PreAuthorize("@authService.isFacilitator(#retroId)")
     */
    public boolean isFacilitator(UUID retroId) {
        try {
            HttpServletRequest request = getCurrentRequest();
            if (request == null) {
                log.warn("No HTTP request context available for security check");
                return false;
            }

            return participantService.isFacilitator(request, retroId);

        } catch (Exception e) {
            log.debug("Facilitator check failed for retro {}: {}", retroId, e.getMessage());
            return false;
        }
    }

    /**
     * Get current HTTP request from Spring's RequestContextHolder.
     * Used by authorization methods to access request context in @PreAuthorize expressions.
     */
    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}