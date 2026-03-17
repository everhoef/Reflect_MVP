package direct.reflect.facilitator.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/test")
@Profile("test")
@Slf4j
public class TestAuthController {

    @RequestMapping(value = "/login-oauth-user", method = {RequestMethod.POST, RequestMethod.GET})
    public ResponseEntity<String> loginOAuthUser(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestParam String username,
            @RequestParam(defaultValue = "Test OAuth User") String displayName,
            @RequestParam(defaultValue = "testuser@example.com") String email) {

        try {
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("login", username);
            attributes.put("name", displayName);
            attributes.put("email", email);
            attributes.put("sub", username);

            OAuth2User oauth2User = new DefaultOAuth2User(
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")),
                attributes,
                "login"
            );

            OAuth2AuthenticationToken authToken = new OAuth2AuthenticationToken(
                oauth2User,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")),
                "github"
            );

            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(authToken);
            SecurityContextHolder.setContext(securityContext);

            HttpSessionSecurityContextRepository repository = new HttpSessionSecurityContextRepository();
            repository.saveContext(securityContext, request, response);

            request.getSession().setAttribute("authenticatedUser", username);
            request.getSession().setAttribute("userEmail", email);
            request.getSession().setAttribute("userDisplayName", displayName);
            request.getSession().setAttribute("authType", "OIDC");
            request.getSession().removeAttribute("guestDisplayName");
            request.getSession().removeAttribute("guestId");

            return ResponseEntity.status(302)
                .header("Location", "/")
                .body("OAuth2 authentication set up for: " + username);

        } catch (Exception e) {
            log.error("Failed to set up OAuth2 test authentication", e);
            return ResponseEntity.internalServerError().body("Authentication setup failed");
        }
    }

    @PostMapping("/login-guest-user")
    public ResponseEntity<String> loginGuestUser(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestParam(defaultValue = "Guest User") String displayName) {

        try {
            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    displayName,
                    null,
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_GUEST"))
                )
            );
            SecurityContextHolder.setContext(securityContext);

            HttpSessionSecurityContextRepository repository = new HttpSessionSecurityContextRepository();
            repository.saveContext(securityContext, request, response);

            request.getSession().setAttribute("guestDisplayName", displayName);
            request.getSession().setAttribute("authType", "GUEST");
            request.getSession().removeAttribute("authenticatedUser");

            return ResponseEntity.ok("Guest authentication set up for: " + displayName);

        } catch (Exception e) {
            log.error("Failed to set up guest test authentication", e);
            return ResponseEntity.internalServerError().body("Guest authentication setup failed");
        }
    }

    @PostMapping("/create-anonymous-session")
    public ResponseEntity<String> createAnonymousSession(HttpServletRequest request) {
        request.getSession(true);
        return ResponseEntity.ok("Anonymous session created");
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        request.getSession().invalidate();
        return ResponseEntity.ok("Authentication cleared");
    }
}
