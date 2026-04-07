package direct.reflect.facilitator.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

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
public class AuthApiController {

    private final AuthService authService;

    public AuthApiController(AuthService authService) {
        this.authService = authService;
    }

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
        String authType = session != null ? (String) session.getAttribute("authType") : null;
        if (authType == null) {
            return ResponseEntity.status(401)
                    .body(Map.of(
                            "error", "Authentication required",
                            "loginUrl", "/login"
                    ));
        }

        boolean isGuest = "GUEST".equals(authType);
        String role = isGuest ? "GUEST" : "USER";

        String participantId = authService.getParticipantId(request).toString();
        String displayName = authService.getDisplayName(request);

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
}
