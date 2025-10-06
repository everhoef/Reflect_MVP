package direct.reflect.facilitator.web;

import java.util.UUID;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import direct.reflect.facilitator.facilitation.RetroSessionService;
import direct.reflect.facilitator.facilitation.RetroSession;
import direct.reflect.facilitator.facilitation.RetroPhase;
import direct.reflect.facilitator.facilitation.ParticipantService;
import direct.reflect.facilitator.facilitation.Participant;
import direct.reflect.facilitator.facilitation.response.ResponseService;
import direct.reflect.facilitator.facilitation.response.ParticipantResponse;
import direct.reflect.facilitator.facilitation.response.CategoricalResponse;
import direct.reflect.facilitator.facilitation.response.RatingResponse;
import direct.reflect.facilitator.facilitation.response.FreeformResponse;
import direct.reflect.facilitator.auth.AuthService;
import direct.reflect.facilitator.configurator.RetroTemplate;
import direct.reflect.facilitator.configurator.RetroStep;
import direct.reflect.facilitator.common.exception.ParticipantNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequiredArgsConstructor
@Slf4j
public class RetroViewController {
    private final RetroSessionService retroService;
    private final ParticipantService participantService;
    private final ResponseService responseService;
    private final AuthService authenticationHelper;

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
        
        String displayName = authenticationHelper.getDisplayName(request);
        
        model.addAttribute("page", "home");
        model.addAttribute("title", "Team Retrospective - Home");
        model.addAttribute("userName", displayName);
        
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
            if (session == null) {
                log.warn("Session not found: {}", retroId);
                return "redirect:/?error=session_not_found";
            }

            // Get participant
            Participant participant = participantService.getParticipantForSession(request, retroId);
            boolean isFacilitator = participantService.isFacilitator(request, retroId);

            // Get all participants
            List<Participant> participants = participantService.getSessionParticipants(retroId);

            // Determine page type based on phase
            String page = (session.getPhase() == RetroPhase.CREATED || session.getPhase() == RetroPhase.LOBBY)
                         ? "lobby" : "retro";

            // Get current step if in retro phase
            RetroStep currentStep = null;
            Integer stepDurationMinutes = null;
            if (session.getPhase() != RetroPhase.CREATED && session.getPhase() != RetroPhase.LOBBY) {
                currentStep = retroService.getCurrentStep(retroId);
                if (currentStep != null && currentStep.getDurationSeconds() != null) {
                    stepDurationMinutes = (int) java.time.Duration.ofSeconds(currentStep.getDurationSeconds()).toMinutes();
                }
            }

            // Add data to model
            model.addAttribute("page", page);
            model.addAttribute("title", "Retrospective: " + session.getName());
            model.addAttribute("session", session);
            model.addAttribute("currentStep", currentStep);
            model.addAttribute("stepDurationMinutes", stepDurationMinutes);
            model.addAttribute("participant", participant);
            model.addAttribute("participants", participants);
            model.addAttribute("isFacilitator", isFacilitator);
            model.addAttribute("userName", participant.getDisplayName());

            log.info("Prepared retro view for session {} - page: {}, phase: {}",
                retroId, page, session.getPhase());

            return "layout";

        } catch (ParticipantNotFoundException e) {
            log.error("Participant not found: ", e);
            return "redirect:/login";
        } catch (Exception e) {
            log.error("Error in retroView: ", e);
            throw e;
        }
    }

    @GetMapping("/retro/{retroId}/participants")
    public String getParticipantsList(@PathVariable UUID retroId, Model model) {
        log.debug("Getting participants list for retro session: {}", retroId);

        try {
            List<Participant> participants = participantService.getSessionParticipants(retroId);
            model.addAttribute("participants", participants);
            return "fragments/participants :: participantsList";

        } catch (Exception e) {
            log.error("Error fetching participants list: ", e);
            model.addAttribute("participants", java.util.Collections.emptyList());
            return "fragments/participants :: participantsList";
        }
    }

    /**
     * Get HTML fragment for categorical responses in a specific category lane.
     * Called by HTMX when SSE events trigger (note_added, note_updated).
     */
    @GetMapping("/retro/{retroId}/step/{stepId}/responses/categorical")
    @PreAuthorize("@authService.canAccessRetro(#retroId)")
    public String getCategoricalResponses(
            @PathVariable UUID retroId,
            @PathVariable Long stepId,
            @RequestParam String category,
            Model model) {

        log.debug("Getting categorical responses for retro: {}, step: {}, category: {}", retroId, stepId, category);

        try {
            RetroSession session = retroService.getSessionById(retroId);
            RetroStep step = retroService.getCurrentStep(retroId);

            if (step == null || !step.getId().equals(stepId)) {
                log.warn("Step mismatch - requested: {}, current: {}", stepId, step != null ? step.getId() : "null");
                return "fragments/response-fragments :: error";
            }

            List<ParticipantResponse> allResponses = responseService.getResponsesForStep(session, step);

            List<CategoricalResponse> categoryResponses = allResponses.stream()
                .filter(r -> r instanceof CategoricalResponse)
                .map(r -> (CategoricalResponse) r)
                .filter(r -> category.equals(r.getCategory()))
                .toList();

            model.addAttribute("responses", categoryResponses);
            model.addAttribute("category", category);

            return "fragments/response-fragments :: categorical-lane-content";

        } catch (Exception e) {
            log.error("Error fetching categorical responses: ", e);
            return "fragments/response-fragments :: error";
        }
    }

    /**
     * Get HTML fragment for all rating responses.
     * Called by HTMX when SSE events trigger (note_added, note_updated).
     */
    @GetMapping("/retro/{retroId}/step/{stepId}/responses/rating")
    @PreAuthorize("@authService.canAccessRetro(#retroId)")
    public String getRatingResponses(
            @PathVariable UUID retroId,
            @PathVariable Long stepId,
            Model model) {

        log.debug("Getting rating responses for retro: {}, step: {}", retroId, stepId);

        try {
            RetroSession session = retroService.getSessionById(retroId);
            RetroStep step = retroService.getCurrentStep(retroId);

            if (step == null || !step.getId().equals(stepId)) {
                return "fragments/response-fragments :: error";
            }

            List<ParticipantResponse> allResponses = responseService.getResponsesForStep(session, step);

            List<RatingResponse> ratingResponses = allResponses.stream()
                .filter(r -> r instanceof RatingResponse)
                .map(r -> (RatingResponse) r)
                .toList();

            model.addAttribute("responses", ratingResponses);

            return "fragments/response-fragments :: rating-list";

        } catch (Exception e) {
            log.error("Error fetching rating responses: ", e);
            return "fragments/response-fragments :: error";
        }
    }

    /**
     * Get HTML fragment for all freeform responses.
     * Called by HTMX when SSE events trigger (note_added, note_updated).
     */
    @GetMapping("/retro/{retroId}/step/{stepId}/responses/freeform")
    @PreAuthorize("@authService.canAccessRetro(#retroId)")
    public String getFreeformResponses(
            @PathVariable UUID retroId,
            @PathVariable Long stepId,
            Model model) {

        log.debug("Getting freeform responses for retro: {}, step: {}", retroId, stepId);

        try {
            RetroSession session = retroService.getSessionById(retroId);
            RetroStep step = retroService.getCurrentStep(retroId);

            if (step == null || !step.getId().equals(stepId)) {
                return "fragments/response-fragments :: error";
            }

            List<ParticipantResponse> allResponses = responseService.getResponsesForStep(session, step);

            List<FreeformResponse> freeformResponses = allResponses.stream()
                .filter(r -> r instanceof FreeformResponse)
                .map(r -> (FreeformResponse) r)
                .toList();

            model.addAttribute("responses", freeformResponses);

            return "fragments/response-fragments :: freeform-list";

        } catch (Exception e) {
            log.error("Error fetching freeform responses: ", e);
            return "fragments/response-fragments :: error";
        }
    }
}
