package direct.reflect.facilitator.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthApiController {

    private final AuthService authService;

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401)
                    .body(Map.of(
                            "error", "Authentication required",
                            "loginUrl", "/login"
                    ));
        }

        HttpSession session = request.getSession(false);
        String authType = resolveAuthType(session, authentication);
        if (authType == null) {
            return unauthorized();
        }

        boolean isGuest = "GUEST".equals(authType);
        String role = isGuest ? "GUEST" : "USER";

        if (authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER"))) {
            role = "MANAGER";
        }

        String participantId;
        String displayName;
        try {
            participantId = authService.getParticipantId(request).toString();
            displayName = authService.getDisplayName(request);
        } catch (IllegalStateException e) {
            return unauthorized();
        }

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", participantId);
        user.put("displayName", displayName);
        user.put("role", role);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("isAuthenticated", true);
        body.put("isGuest", isGuest);
        body.put("authType", authType);
        body.put("user", user);

        return ResponseEntity.ok(body);
    }

    private String resolveAuthType(HttpSession session, Authentication authentication) {
        if (session == null) {
            return null;
        }

        String authType = (String) session.getAttribute("authType");
        if (authType != null) {
            return authType;
        }

        if (session.getAttribute("authenticatedUser") == null || session.getAttribute("userDisplayName") == null) {
            return null;
        }

        boolean isGuest = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_GUEST"));

        String derivedAuthType = isGuest ? "GUEST" : "OIDC";
        session.setAttribute("authType", derivedAuthType);

        return derivedAuthType;
    }

    private ResponseEntity<Map<String, String>> unauthorized() {
        return ResponseEntity.status(401)
                .body(Map.of(
                        "error", "Authentication required",
                        "loginUrl", "/login"
                ));
    }
}
