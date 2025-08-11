package direct.reflect.facilitator.web;

import java.util.UUID;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import direct.reflect.facilitator.facilitation.RetroSessionService;
import direct.reflect.facilitator.facilitation.ParticipantService;
import direct.reflect.facilitator.facilitation.AuthenticationService;
import direct.reflect.facilitator.facilitation.RetroSession;
import direct.reflect.facilitator.facilitation.Participant;
import direct.reflect.facilitator.configurator.RetroStage;
import direct.reflect.facilitator.configurator.RetroTemplate;
import direct.reflect.facilitator.common.exception.ParticipantNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequiredArgsConstructor
@Slf4j
public class RetroViewController {
    private final RetroSessionService retroService;
    private final ParticipantService participantService;
    private final AuthenticationService authenticationService;

    @GetMapping("/")
    public String home(Model model, HttpServletRequest request) {
        // Check if this is an SSE request - home page should never handle SSE
        String acceptHeader = request.getHeader("Accept");
        if (acceptHeader != null && acceptHeader.contains("text/event-stream")) {
            log.warn("SSE request made to home page - this is incorrect. Accept header: {}", acceptHeader);
            throw new IllegalArgumentException("SSE requests should be made to /api/retro/{retroId}/events, not /home");
        }

        // Home page requires authentication - Spring Security will redirect to /login if not authenticated
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new IllegalStateException("No authentication context found");
        }
        
        String displayName = authenticationService.extractDisplayName(auth);
        
        model.addAttribute("page", "home");
        model.addAttribute("title", "Team Retrospective - Home");
        model.addAttribute("user", displayName);
        
        List<RetroTemplate> templates = retroService.getAvailableTemplates();
        model.addAttribute("templates", templates);
        
        return "layout";
    }

    @GetMapping("/login")
    public String loginPage(Model model) {
        model.addAttribute("page", "welcome");
        model.addAttribute("title", "Team Retrospective - Login");
        return "layout";
    }


    @GetMapping("/retro/{retroId}")
    public String retroView(@PathVariable UUID retroId, Model model, HttpServletRequest request) {
        try {
            // Get session 
            RetroSession session = retroService.getSessionById(retroId);
            
            // Get current participant (will throw ParticipantNotFoundException if not participating)
            Participant participant = participantService.getParticipantForSession(request, retroId);
            log.info("Got participant: {}", participant);
            
            // Get facilitator status
            boolean isFacilitator = participantService.isFacilitator(request, retroId);
            log.info("Got facilitator status: {}", isFacilitator);
            
            // Default page to prevent null template references
            String page = "lobby";
            
            // Log current phase for debugging
            log.info("Session phase: {}, retroId: {}", session.getPhase(), retroId);
            
            // Set page based on phase
            switch(session.getPhase()) {
                case CREATED -> {
                    page = "lobby"; // Show lobby while creating
                }
                case LOBBY -> {
                    page = "lobby";
                }
                case SET_THE_STAGE, GATHER_DATA, GENERATE_INSIGHTS, DECIDE_ACTIONS, CLOSE_RETRO -> {
                    page = "retro";
                }
                case PAUSED, COMPLETED, ABANDONED -> {
                    // For now, just show the retro page for these states
                    // TODO: Create specialized templates later
                    page = "retro";
                }
                default -> {
                    log.warn("Unknown phase: {}, defaulting to lobby", session.getPhase());
                    page = "lobby";
                }
            }
            
            // Log the final page attribute with more context
            log.info("Final page attribute: {}, session phase: {}", page, session.getPhase());
            log.info("All modelAttributes being passed to template: page={}, retroId={}, title={}, isFacilitator={}", 
                page, retroId, session.getName(), isFacilitator);
            
            // Add basic model attributes
            model.addAttribute("retroId", retroId);
            model.addAttribute("title", session.getName());
            model.addAttribute("session", session);
            model.addAttribute("participant", participant);
            model.addAttribute("userName", participant.getDisplayName());
            model.addAttribute("isFacilitator", isFacilitator);
            model.addAttribute("page", page);
            
            // Add phase-specific attributes
            switch(session.getPhase()) {
                case CREATED, LOBBY -> {
                    model.addAttribute("participants", participantService.getSessionParticipants(retroId));
                }
                case SET_THE_STAGE, GATHER_DATA, GENERATE_INSIGHTS, DECIDE_ACTIONS, CLOSE_RETRO -> {
                    // These phases have stages - get the current stage from template
                    RetroStage currentStage = session.getCurrentStage();
                    model.addAttribute("currentPhase", session.getPhase());
                    model.addAttribute("currentStage", currentStage);
                    model.addAttribute("template", session.getTemplate());
                }
                case PAUSED, COMPLETED, ABANDONED -> {
                    // These phases don't have stages, show minimal retro page
                    model.addAttribute("currentPhase", session.getPhase());
                }
            }
            
            return "layout";
            
        } catch (ParticipantNotFoundException e) {
            log.error("Error in getParticipantForSession chain: ", e);
            // Redirect to join page or login based on authentication
            return "redirect:/login";
        } catch (Exception e) {
            log.error("Error in retroView: ", e);
            throw e;
        }
    }

    @GetMapping("/retro/{retroId}/participants")
    public String getParticipantsList(@PathVariable UUID retroId, Model model) {
        log.debug("Getting participants list for retro session: {}", retroId);
        
        // Verify session exists first - this will throw RetroSessionNotFoundException if not found
        retroService.getSessionById(retroId);
        
        model.addAttribute("participants", participantService.getSessionParticipants(retroId));
        return "fragments/lobby :: ul.space-y-2";  // Direct fragment reference
    }
    
    @PostMapping("/logout")
    public String logout(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            log.info("Logging out user: {} (type: {})", 
                auth.getName(), auth.getClass().getSimpleName());
        }
        
        authenticationService.clearAuthenticationCookies(request);
        
        return "redirect:/login";
    }
    
    /**
     * Unified login endpoint - delegates to AuthenticationService for business logic.
     */
    @PostMapping("/login")
    public String unifiedLogin(HttpServletRequest request, 
                              String loginType, 
                              String username, 
                              String password, 
                              String displayName) {
        
        log.info("=== UNIFIED LOGIN DEBUG ===");
        log.info("Method: {}", request.getMethod());
        log.info("Path: {}", request.getRequestURI());
        log.info("Query parameters: {}", request.getQueryString());
        
        log.info("Form data - loginType: {}, username: {}, displayName: {}", 
                loginType, username, displayName);
        
        String result = authenticationService.processLogin(loginType, username, password, displayName, request);
        log.info("Login result: {}", result);
        
        return result;
    }
    
}
