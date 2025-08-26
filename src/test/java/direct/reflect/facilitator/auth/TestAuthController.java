package direct.reflect.facilitator.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import direct.reflect.facilitator.auth.AuthenticationHelper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Test-only authentication controller for integration tests.
 * Allows setting up different authentication contexts for multi-user testing.
 */
@RestController
@RequestMapping("/test")
@Profile("test")
@RequiredArgsConstructor
@Slf4j
public class TestAuthController {
    
    private final AuthenticationHelper authenticationHelper;
    
    /**
     * Sets up OAuth2/OIDC authentication for a test user.
     * Returns session cookie that can be used in browser tests.
     */
    @PostMapping("/login-oauth-user")
    public ResponseEntity<String> loginOAuthUser(
            HttpServletRequest request, 
            @RequestParam String username,
            @RequestParam(defaultValue = "Test OAuth User") String displayName,
            @RequestParam(defaultValue = "testuser@example.com") String email) {
        
        log.info("Setting up OAuth2 test authentication for user: {}", username);
        
        try {
            // Create OAuth2 user attributes (simulating GitHub response)
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("login", username);  // GitHub username field
            attributes.put("name", displayName);
            attributes.put("email", email);
            attributes.put("sub", username);
            
            // Create OAuth2User
            OAuth2User oauth2User = new DefaultOAuth2User(
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")),
                attributes,
                "login"  // nameAttributeKey for GitHub
            );
            
            // Create OAuth2 authentication token
            OAuth2AuthenticationToken authToken = new OAuth2AuthenticationToken(
                oauth2User,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")),
                "github"  // registrationId
            );
            
            // Set up security context
            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(authToken);
            SecurityContextHolder.setContext(securityContext);
            
            // Store in session
            request.getSession().setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, 
                securityContext
            );
            
            // Set up session attributes that OidcSuccessHandler would normally set
            request.getSession().setAttribute("authenticatedUser", username);
            request.getSession().setAttribute("userEmail", email);
            request.getSession().setAttribute("userDisplayName", displayName);
            request.getSession().setAttribute("authType", "OIDC");
            
            // Clear any guest session data
            request.getSession().removeAttribute("guestDisplayName");
            request.getSession().removeAttribute("guestId");
            
            // Debug: Log what we've set in the session
            log.info("OAuth2 test authentication successful for user: {}", username);
            log.info("Session attributes set: authenticatedUser={}, userDisplayName={}, authType={}", 
                     request.getSession().getAttribute("authenticatedUser"),
                     request.getSession().getAttribute("userDisplayName"),
                     request.getSession().getAttribute("authType"));
            
            return ResponseEntity.ok("OAuth2 authentication set up for: " + username);
            
        } catch (Exception e) {
            log.error("Failed to set up OAuth2 test authentication", e);
            return ResponseEntity.internalServerError().body("Authentication setup failed");
        }
    }
    
    /**
     * Sets up guest authentication for a test user.
     * Returns session cookie that can be used in browser tests.
     */
    @PostMapping("/login-guest")
    public ResponseEntity<String> loginGuest(
            HttpServletRequest request,
            @RequestParam String displayName) {
        
        log.info("Setting up guest test authentication for: {}", displayName);
        
        try {
            // Use the existing AuthenticationHelper to set up guest session
            authenticationHelper.initializeGuestSession(request, displayName);
            
            log.info("Guest test authentication successful for: {}", displayName);
            return ResponseEntity.ok("Guest authentication set up for: " + displayName);
            
        } catch (Exception e) {
            log.error("Failed to set up guest test authentication", e);
            return ResponseEntity.internalServerError().body("Guest authentication setup failed");
        }
    }
    
    /**
     * Creates an unauthenticated session (for testing unauthorized access).
     */
    @PostMapping("/create-anonymous-session")
    public ResponseEntity<String> createAnonymousSession(HttpServletRequest request) {
        log.info("Creating anonymous test session");
        
        // Just create a session without any authentication
        request.getSession(true);
        
        return ResponseEntity.ok("Anonymous session created");
    }
    
    /**
     * Clears authentication from the current session.
     */
    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpServletRequest request) {
        log.info("Clearing test authentication");
        
        SecurityContextHolder.clearContext();
        request.getSession().invalidate();
        
        return ResponseEntity.ok("Authentication cleared");
    }
}