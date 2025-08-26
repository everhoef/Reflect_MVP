package direct.reflect.facilitator.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Authentication Controller - handles guest authentication and logout.
 * 
 * Responsibilities:
 * - Guest authentication (/auth/guest)
 * - User logout (/auth/logout)
 */
@Controller
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    
    private final AuthenticationHelper authenticationHelper;

    /**
     * Guest authentication endpoint.
     * Handles guest login for both DEV and PROD environments.
     */
    @PostMapping("/guest")
    public String authenticateGuest(HttpServletRequest request, String displayName) {
        
        log.info("=== GUEST AUTHENTICATION ===");
        log.info("Display name: {}", displayName);
        
        if (displayName == null || displayName.trim().isEmpty()) {
            return "redirect:/login?error=missing_display_name";
        }
        
        try {
            // Initialize guest session - same structure as OIDC users for consistency
            authenticationHelper.initializeGuestSession(request, displayName.trim());
            log.info("Guest '{}' successfully authenticated", displayName.trim());
            
            return "redirect:/";
        } catch (Exception e) {
            log.error("Guest authentication failed", e);
            return "redirect:/login?error=guest_auth_failed";
        }
    }

    /**
     * Logout endpoint for both USER and GUEST authentication types.
     */
    @PostMapping("/logout")
    public String logout(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            log.info("Logging out user: {} (type: {})", 
                auth.getName(), auth.getClass().getSimpleName());
        }
        
        // Spring Security logout handles session clearing automatically
        return "redirect:/login";
    }
}