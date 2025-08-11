package direct.reflect.facilitator.facilitation;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import direct.reflect.facilitator.common.RequestValidationService;

import java.util.List;
import java.util.UUID;

/**
 * Service responsible for handling authentication operations.
 * Uses Spring Security sessions with Redis for scalable authentication.
 * Supports both authenticated users and guest users.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {
    
    private final AuthenticationManager authenticationManager;
    private final RequestValidationService validationService;
    
    // Session attribute keys  
    private static final String GUEST_ID_ATTR = "guestId";

    /**
     * Processes login form data and performs appropriate authentication.
     * 
     * @param loginType "guest" or "user"
     * @param username username for user authentication (can be null for guest)
     * @param password password for user authentication (can be null for guest)
     * @param displayName display name for guest authentication (can be null for user)
     * @param request the HTTP servlet request
     * @return redirect path
     */
    public String processLogin(String loginType, String username, String password, 
                              String displayName, HttpServletRequest request) {
        
        log.info("Processing login - type: {}, username: {}, displayName: {}", 
                loginType, username, displayName);
        
        var loginTypeValidation = validationService.validateLoginType(loginType);
        if (!loginTypeValidation.isValid()) {
            return "redirect:/login?error=" + loginTypeValidation.getErrorCode();
        }
        
        return switch (loginType.toLowerCase()) {
            case "guest" -> authenticateGuest(displayName, request);
            case "user" -> authenticateUser(username, password, request);
            default -> "redirect:/login?error=invalid_login_type";
        };
    }

    /**
     * Authenticates a guest user using standard Spring Security with session storage.
     * Creates UsernamePasswordAuthenticationToken with ROLE_GUEST and stores guest ID in session.
     * 
     * @param displayName the display name for the guest
     * @param request the HTTP servlet request
     * @return redirect path
     */
    public String authenticateGuest(String displayName, HttpServletRequest request) {
        var validation = validationService.validateDisplayName(displayName);
        if (!validation.isValid()) {
            return "redirect:/login?error=" + validation.getErrorCode();
        }
        
        // Generate or reuse guest ID from existing session
        UUID guestId = getOrCreateGuestId(request);
        
        // Create standard Spring Security authentication token with GUEST role
        Authentication guestAuth = new UsernamePasswordAuthenticationToken(
            displayName.trim(), 
            null, // No credentials for guests
            List.of(new SimpleGrantedAuthority("ROLE_GUEST"))
        );
        
        // Set security context
        SecurityContext context = new SecurityContextImpl(guestAuth);
        SecurityContextHolder.setContext(context);
        
        // Save security context to session for persistence
        HttpSession session = request.getSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        
        // Store guest ID in session
        session.setAttribute(GUEST_ID_ATTR, guestId);
        
        log.info("Guest authenticated: {} (guestId: {})", displayName, guestId);
        return "redirect:/";
    }

    /**
     * Authenticates a user using standard Spring Security with credential validation.
     * Uses AuthenticationManager for proper credential validation.
     * 
     * @param username the username
     * @param password the password
     * @param request the HTTP servlet request
     * @return redirect path
     */
    public String authenticateUser(String username, String password, HttpServletRequest request) {
        var validation = validationService.validateUserCredentials(username, password);
        if (!validation.isValid()) {
            return "redirect:/login?error=" + validation.getErrorCode();
        }
        
        try {
            // Create authentication token for validation
            Authentication userAuth = new UsernamePasswordAuthenticationToken(username.trim(), password);
            
            // Authenticate through Spring Security - this will validate credentials and assign ROLE_USER
            Authentication authenticatedAuth = authenticationManager.authenticate(userAuth);
            
            // Set security context
            SecurityContext context = new SecurityContextImpl(authenticatedAuth);
            SecurityContextHolder.setContext(context);
            
            // Save security context to session for persistence
            HttpSession session = request.getSession();
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
            
            // For authenticated users, we don't store additional identifiers in session
            // Username from authentication is sufficient and naturally persistent
            
            log.info("User authenticated: {}", username);
            return "redirect:/";
            
        } catch (Exception e) {
            log.warn("Authentication failed for user: {} - {}", username, e.getMessage());
            return "redirect:/login?error=invalid_credentials";
        }
    }

    /**
     * Clears authentication session data and invalidates session.
     * 
     * @param request the HTTP servlet request
     */
    public void clearAuthenticationCookies(HttpServletRequest request) {
        // Clear security context
        SecurityContextHolder.clearContext();
        
        // Invalidate session (this will clear all session attributes including guestId)
        HttpSession session = request.getSession(false);
        if (session != null) {
            log.debug("Invalidating session for logout");
            session.invalidate();
        }
        
        log.debug("Cleared authentication data and invalidated session");
    }

    /**
     * Extracts display name from standard Spring Security authentication context.
     * Works for both GUEST (displayName) and USER (username).
     * 
     * @param auth the authentication object
     * @return the display name
     */
    public String extractDisplayName(Authentication auth) {
        return auth.getName(); // Works for both GUEST and USER
    }
    
    /**
     * Gets the user identifier for database lookups.
     * For authenticated users: returns username
     * For guests: returns guestId from session
     * 
     * @param request the HTTP servlet request
     * @return the user identifier
     */
    public String getUserIdentifier(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new IllegalStateException("No authentication context found");
        }
        
        if (isGuest()) {
            UUID guestId = getGuestId(request);
            if (guestId == null) {
                throw new IllegalStateException("Guest ID not found in session");
            }
            return guestId.toString();
        } else {
            return auth.getName(); // username for authenticated users
        }
    }
    
    /**
     * Gets guest ID from session.
     * 
     * @param request the HTTP servlet request
     * @return the guest ID or null if not found
     */
    public UUID getGuestId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null ? (UUID) session.getAttribute(GUEST_ID_ATTR) : null;
    }
    
    /**
     * Checks if current user is a guest by examining Spring Security authorities.
     * 
     * @return true if user has ROLE_GUEST authority, false otherwise
     */
    public boolean isGuest() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        
        return auth.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("ROLE_GUEST"));
    }
    
    /**
     * Gets or creates a guest ID for the current session.
     * Reuses existing guest ID if present in session for consistency.
     * 
     * @param request the HTTP servlet request
     * @return the guest ID
     */
    private UUID getOrCreateGuestId(HttpServletRequest request) {
        HttpSession session = request.getSession();
        UUID guestId = (UUID) session.getAttribute(GUEST_ID_ATTR);
        
        if (guestId == null) {
            guestId = UUID.randomUUID();
            session.setAttribute(GUEST_ID_ATTR, guestId);
            log.debug("Created new guest ID: {}", guestId);
        } else {
            log.debug("Reusing existing guest ID: {}", guestId);
        }
        
        return guestId;
    }
}