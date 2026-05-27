package direct.reflect.facilitator.auth;

import direct.reflect.facilitator.organization.ManagerTeamAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Component;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

/**
 * Authentication Service for OIDC + Guest hybrid model.
 *
 * Handles authentication concerns:
 * - Extracts user identity from HTTP sessions for OIDC and Guest users
 * - Manages guest session initialization
 * - Provides consistent identity APIs for both auth types
 *
 * Authorization is handled by ParticipantService.
 *
 * Used by:
 * - Controllers for identity extraction
 * - ParticipantService for user identity lookups
 */
@Component("authService")
@Slf4j
@RequiredArgsConstructor
public class AuthService {
    private static final UUID OIDC_USER_ID_NAMESPACE = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");

    private final ManagerTeamAccess managerTeamAccess;

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
            UUID participantId = toOidcUserId(username);
            log.debug("Retrieved OIDC participantId: {} for username: {}, httpSessionId: {}",
                participantId, username, session.getId());
            return participantId;
        } else {
            UUID participantId = getOrCreateGuestId(session);
            log.debug("Retrieved GUEST participantId: {}, httpSessionId: {}",
                participantId, session.getId());
            return participantId;
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

    public Set<GrantedAuthority> resolveOidcAuthorities(Collection<? extends GrantedAuthority> existingAuthorities,
                                                        String username) {
        Set<GrantedAuthority> authorities = new HashSet<>(existingAuthorities);
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

        if (hasManagerRole(username)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_MANAGER"));
        }

        return authorities;
    }

    public boolean hasManagerRole(String username) {
        UUID userId = toOidcUserId(username);
        return managerTeamAccess.hasManagerRole(userId);
    }

    public Optional<UUID> findSingleManagedTeamId(HttpServletRequest request) {
        String username = getUsername(request);
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }

        UUID userId = toOidcUserId(username);
        return managerTeamAccess.findSingleManagedTeamId(userId);
    }

    public UUID toOidcUserId(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("OIDC username cannot be blank");
        }

        return UUID.nameUUIDFromBytes((OIDC_USER_ID_NAMESPACE + username).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Initialize guest session using unified session structure.
     * Allows duplicate display names (like Zoom/Teams) - backend uses unique UUIDs.
     */
    public void initializeGuestSession(HttpServletRequest request, String displayName) {
        if (displayName == null || displayName.trim().isEmpty()) {
            throw new IllegalArgumentException("Guest display name cannot be empty");
        }

        initializeGuestSession(request, displayName, UUID.randomUUID());
    }

    public void initializeGuestSession(HttpServletRequest request, String displayName, UUID guestId) {
        if (displayName == null || displayName.trim().isEmpty()) {
            throw new IllegalArgumentException("Guest display name cannot be empty");
        }
        if (guestId == null) {
            throw new IllegalArgumentException("Guest ID cannot be null");
        }

        HttpSession session = request.getSession(true);
        String trimmedDisplayName = displayName.trim();

        // Generate unique guest ID (acts as the "username" for guests)
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

}
