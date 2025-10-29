package direct.reflect.facilitator.web;

import java.util.UUID;
import java.util.List;
import java.util.Map;

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
import direct.reflect.facilitator.facilitation.dto.RatingDto;
import direct.reflect.facilitator.configurator.DataPattern;
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
            model.addAttribute("retroSession", session);
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

    /**
     * Get only the retro/lobby fragment content for HTMX partial updates.
     * This avoids duplicating headers and other layout elements.
     */
    @GetMapping("/retro/{retroId}/content")
    public String retroContentFragment(@PathVariable UUID retroId, Model model, HttpServletRequest request) {
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
            model.addAttribute("retroSession", session);
            model.addAttribute("currentStep", currentStep);
            model.addAttribute("stepDurationMinutes", stepDurationMinutes);
            model.addAttribute("participant", participant);
            model.addAttribute("participants", participants);
            model.addAttribute("isFacilitator", isFacilitator);

            log.debug("Returning fragment for session {} - page: {}, phase: {}",
                retroId, page, session.getPhase());

            // Return only the inner content (not the wrapper div to preserve SSE connection)
            return page.equals("lobby") ? "fragments/lobby :: content" : "fragments/retro :: inner-content";

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
    @PreAuthorize("@participantService.canAccessRetro(#retroId)")
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

            List<ParticipantResponse> categoryResponses = allResponses.stream()
                .filter(r -> r.getDataPattern() == DataPattern.CATEGORICAL)
                .filter(r -> category.equals(r.getResponseData().get("category")))
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
    @GetMapping("/retro/{retroId}/step/{stepId}/histogram")
    // @PreAuthorize("@participantService.canAccessRetro(#retroId)") // Temporarily disabled for debugging
    public String getRatingHistogram(
            @PathVariable UUID retroId,
            @PathVariable Long stepId,
            Model model,
            HttpServletRequest request) {

        log.debug("Getting rating histogram for retro: {}, step: {}", retroId, stepId);
        log.trace("Method entry - about to fetch session");

        try {
            RetroSession session = retroService.getSessionById(retroId);
            log.trace("Fetched session: {}", session != null ? session.getId() : "null");

            RetroStep step = retroService.getCurrentStep(retroId);
            log.trace("Fetched current step: {}", step != null ? step.getId() : "null");

            if (step == null || !step.getId().equals(stepId)) {
                return "fragments/retro-rating :: histogram-error";
            }

            List<ParticipantResponse> allResponses = responseService.getResponsesForStep(session, step);

            // Convert entities to DTOs for clean Thymeleaf rendering
            List<RatingDto> ratingDtos = allResponses.stream()
                .filter(r -> r.getDataPattern() == DataPattern.RATING)
                .map(RatingDto::from)
                .toList();

            log.debug("Converted {} responses to RatingDtos", ratingDtos.size());
            ratingDtos.forEach(dto -> log.debug("RatingDto: id={}, rating={}, visible={}", dto.id(), dto.rating(), dto.visible()));

            // Get current participant to check visibility
            boolean isFacilitator = participantService.isFacilitator(request, retroId);

            // Filter visible responses (facilitator sees all, others see only visible)
            List<RatingDto> visibleResponses = isFacilitator
                ? ratingDtos
                : ratingDtos.stream().filter(RatingDto::visible).toList();

            // Get scale configuration
            Map<String, Object> config = step.getConfig();
            Map<String, Object> scaleConfig = config != null ? (Map<String, Object>) config.get("scale") : null;
            int minRating = scaleConfig != null && scaleConfig.get("min") != null ? ((Number) scaleConfig.get("min")).intValue() : 1;
            int maxRating = scaleConfig != null && scaleConfig.get("max") != null ? ((Number) scaleConfig.get("max")).intValue() : 10;

            model.addAttribute("totalResponses", ratingDtos.size());
            model.addAttribute("responses", visibleResponses);
            model.addAttribute("minRating", minRating);
            model.addAttribute("maxRating", maxRating);
            model.addAttribute("isFacilitator", isFacilitator);

            return "fragments/retro-rating :: histogram";

        } catch (Exception e) {
            log.error("Error fetching rating histogram: ", e);
            return "fragments/retro-rating :: histogram-error";
        }
    }

    /**
     * Get HTML fragment for all freeform responses.
     * Called by HTMX when SSE events trigger (note_added, note_updated).
     */
    @GetMapping("/retro/{retroId}/step/{stepId}/responses/freeform")
    @PreAuthorize("@participantService.canAccessRetro(#retroId)")
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

            List<ParticipantResponse> freeformResponses = allResponses.stream()
                .filter(r -> r.getDataPattern() == DataPattern.FREEFORM)
                .toList();

            model.addAttribute("responses", freeformResponses);

            return "fragments/response-fragments :: freeform-list";

        } catch (Exception e) {
            log.error("Error fetching freeform responses: ", e);
            return "fragments/response-fragments :: error";
        }
    }
}
