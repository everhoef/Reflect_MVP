package direct.reflect.facilitator.web;

import java.util.UUID;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
import direct.reflect.facilitator.facilitation.dto.RatingResponseDto;
import direct.reflect.facilitator.facilitation.dto.ColumnResponseDto;
import direct.reflect.facilitator.facilitation.dto.StageProgressDto;
import direct.reflect.facilitator.facilitation.dto.TimerStateDto;
import direct.reflect.facilitator.facilitation.actionitem.ActionItemService;
import direct.reflect.facilitator.facilitation.actionitem.ActionItemDto;
import direct.reflect.facilitator.auth.AuthService;
import direct.reflect.facilitator.configurator.RetroTemplate;
import direct.reflect.facilitator.configurator.RetroStep;
import direct.reflect.facilitator.configurator.ComponentType;
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
    private final ActionItemService actionItemService;

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
            List<String> instructionHistory = List.of();
            if (session.getPhase() != RetroPhase.CREATED && session.getPhase() != RetroPhase.LOBBY) {
                currentStep = retroService.getCurrentStep(retroId);
                if (currentStep != null && currentStep.getDurationSeconds() != null) {
                    stepDurationMinutes = (int) java.time.Duration.ofSeconds(currentStep.getDurationSeconds()).toMinutes();
                }
                instructionHistory = retroService.getInstructionHistory(retroId);
            }

            // Add data to model
            model.addAttribute("page", page);
            model.addAttribute("title", "Retrospective: " + session.getName());
            model.addAttribute("retroSession", session);
            model.addAttribute("currentStep", currentStep);
            model.addAttribute("stepDurationMinutes", stepDurationMinutes);
            model.addAttribute("instructionHistory", instructionHistory);
            model.addAttribute("participant", participant);
            model.addAttribute("participants", participants);
            model.addAttribute("isFacilitator", isFacilitator);
            model.addAttribute("userName", participant.getDisplayName());
            model.addAttribute("stageProgress", StageProgressDto.forSession(session));
            model.addAttribute("timerState", retroService.getTimerState(retroId));

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
            List<String> instructionHistory = List.of();
            if (session.getPhase() != RetroPhase.CREATED && session.getPhase() != RetroPhase.LOBBY) {
                currentStep = retroService.getCurrentStep(retroId);
                if (currentStep != null && currentStep.getDurationSeconds() != null) {
                    stepDurationMinutes = (int) java.time.Duration.ofSeconds(currentStep.getDurationSeconds()).toMinutes();
                }
                instructionHistory = retroService.getInstructionHistory(retroId);
            }

            // Add data to model
            model.addAttribute("page", page);
            model.addAttribute("retroSession", session);
            model.addAttribute("currentStep", currentStep);
            model.addAttribute("stepDurationMinutes", stepDurationMinutes);
            model.addAttribute("instructionHistory", instructionHistory);
            model.addAttribute("participant", participant);
            model.addAttribute("participants", participants);
            model.addAttribute("isFacilitator", isFacilitator);
            model.addAttribute("stageProgress", StageProgressDto.forSession(session));
            model.addAttribute("timerState", retroService.getTimerState(retroId));

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
     * Get HTML fragment for responses in a specific column of a multi-column board.
     * Used by MULTI_COLUMN_BOARD component for any number of columns (1-N).
     * Called by HTMX when SSE events trigger (response_submitted, responses_revealed).
     */
    @GetMapping("/retro/{retroId}/step/{stepId}/responses/column")
    @PreAuthorize("@participantService.canAccessRetro(#retroId)")
    public String getColumnResponses(
            @PathVariable UUID retroId,
            @PathVariable Long stepId,
            @RequestParam String columnId,
            Model model,
            HttpServletRequest request) {

        log.debug("Getting column responses for retro: {}, step: {}, columnId: {}", retroId, stepId, columnId);

        try {
            RetroSession session = retroService.getSessionById(retroId);
            RetroStep step = retroService.getCurrentStep(retroId);

            if (step == null || !step.getId().equals(stepId)) {
                log.warn("Step mismatch - requested: {}, current: {}", stepId, step != null ? step.getId() : "null");
                return "fragments/common-fragments :: error";
            }

            // Get ALL responses for MULTI_COLUMN_BOARD within this stage
            // (not just current step - clustering/voting steps need to show input step responses)
            List<ParticipantResponse> allResponses = responseService.getResponsesForStageComponentType(
                session,
                step.getRetroStage(),
                ComponentType.MULTI_COLUMN_BOARD
            );
            List<ParticipantResponse> columnResponses = allResponses.stream()
                .filter(r -> columnId.equals(r.getResponseData().get("columnId")))
                .toList();

            // Convert to DTOs for clean template rendering
            List<ColumnResponseDto> responseDtos = columnResponses.stream()
                .map(ColumnResponseDto::from)
                .toList();

            // Get current participant ID for self-view logic in template
            UUID currentParticipantId = authenticationHelper.getParticipantId(request);

            // Get capabilities config from step
            Map<String, Object> capabilities = (Map<String, Object>) step.getComponentConfig().get("capabilities");
            boolean showAuthor = capabilities != null && Boolean.TRUE.equals(capabilities.get("showAuthor"));

            model.addAttribute("retroSession", session);
            model.addAttribute("responses", responseDtos);
            model.addAttribute("currentParticipantId", currentParticipantId);
            model.addAttribute("showAuthor", showAuthor);
            model.addAttribute("capabilities", capabilities != null ? capabilities : Map.of());
            model.addAttribute("currentStep", step);

            return "fragments/components/multi-column-board :: lane-content";

        } catch (Exception e) {
            log.error("Error fetching column responses: ", e);
            return "fragments/common-fragments :: error";
        }
    }

     /**
      * Get HTML fragment for all rating responses in a histogram chart.
      * Called by HTMX when SSE events trigger (response_submitted, responses_revealed).
      */
     @GetMapping("/retro/{retroId}/step/{stepId}/histogram")
     @PreAuthorize("@participantService.canAccessRetro(#retroId)")
     public String getRatingHistogram(
             @PathVariable UUID retroId,
             @PathVariable Long stepId,
             Model model,
             HttpServletRequest request) {

         log.debug("Getting rating histogram for retro: {}, step: {}", retroId, stepId);

         try {
             RetroSession session = retroService.getSessionById(retroId);
             RetroStep step = retroService.getCurrentStep(retroId);

             if (step == null || !step.getId().equals(stepId)) {
                 return "fragments/common-fragments :: error";
             }

             // HISTOGRAM_CHART displays RATING_SCALE responses from the same stage
             List<ParticipantResponse> allResponses = responseService.getResponsesForStageComponentType(
                 session,
                 step.getRetroStage(),
                 ComponentType.RATING_SCALE
             );

             List<RatingResponseDto> ratingDtos = allResponses.stream()
                 .filter(r -> r.getResponseData().containsKey("rating"))
                 .map(RatingResponseDto::from)
                 .toList();

             log.debug("Converted {} responses to RatingDtos", ratingDtos.size());

             // Get current participant to check visibility
             boolean isFacilitator = participantService.isFacilitator(request, retroId);
             log.debug("isFacilitator={}, will show {} of {} responses", isFacilitator,
                 isFacilitator ? ratingDtos.size() : ratingDtos.stream().filter(RatingResponseDto::visible).count(),
                 ratingDtos.size());

             // Filter visible responses (facilitator sees all, others see only visible)
             List<RatingResponseDto> visibleResponses = isFacilitator
                 ? ratingDtos
                 : ratingDtos.stream().filter(RatingResponseDto::visible).toList();

             // Get scale configuration from componentConfig
             Map<String, Object> config = step.getComponentConfig();
             int minRating = config.get("min") != null ? ((Number) config.get("min")).intValue() : 1;
             int maxRating = config.get("max") != null ? ((Number) config.get("max")).intValue() : 10;
             boolean showComments = Boolean.TRUE.equals(config.get("showComments"));

             model.addAttribute("totalResponses", ratingDtos.size());
             model.addAttribute("responses", visibleResponses);
             model.addAttribute("minRating", minRating);
             model.addAttribute("maxRating", maxRating);
             model.addAttribute("showComments", showComments);
             model.addAttribute("isFacilitator", isFacilitator);

             return "fragments/components/histogram-chart :: histogram-data";

         } catch (Exception e) {
             log.error("Error fetching rating histogram: ", e);
             return "fragments/common-fragments :: error";
         }
     }

     /**
      * Get HTML fragment for timer countdown display.
      * Called by HTMX when SSE events trigger (timer_paused, timer_started, step_advanced).
      */
     @GetMapping("/retro/{retroId}/timer-fragment")
     @PreAuthorize("@participantService.canAccessRetro(#retroId)")
     public String getTimerFragment(
             @PathVariable UUID retroId,
             Model model,
             HttpServletRequest request) {

         log.debug("Getting timer fragment for retro: {}", retroId);

         try {
             RetroSession session = retroService.getSessionById(retroId);
             RetroStep currentStep = retroService.getCurrentStep(retroId);

             // Timer only renders when there's an active step with a timer
             TimerStateDto timerState = null;
             if (currentStep != null) {
                 timerState = retroService.getTimerState(retroId);
             }

             model.addAttribute("timerState", timerState);
             model.addAttribute("isFacilitator", participantService.isFacilitator(request, retroId));
             model.addAttribute("retroSession", session);

             log.debug("Returning timer fragment for retro: {} - timerState: {}", retroId, timerState);
             return "fragments/components/timer-countdown :: content";

         } catch (Exception e) {
             log.error("Error fetching timer fragment: ", e);
             return "fragments/common-fragments :: error";
         }
     }

     @PostMapping("/retro/{retroId}/step/{stepId}/action-items/form")
     @PreAuthorize("@participantService.canAccessRetro(#retroId)")
     public ResponseEntity<Void> createActionItemForm(
             @PathVariable UUID retroId,
             @PathVariable Long stepId,
             @ModelAttribute ActionItemDto dto,
             HttpServletRequest request) {

         UUID participantId = authenticationHelper.getParticipantId(request);
         dto.setCreatedByParticipantId(participantId);
         actionItemService.createActionItem(retroId, stepId, dto);
         return ResponseEntity.noContent().build();
     }

     @GetMapping("/retro/{retroId}/action-items")
     @PreAuthorize("@participantService.canAccessRetro(#retroId)")
     public String getActionItemsList(
             @PathVariable UUID retroId,
             Model model) {

         log.debug("Getting action items list for retro: {}", retroId);

         try {
             List<ActionItemDto> actionItems = actionItemService.getActionItemsBySession(retroId);
             model.addAttribute("actionItems", actionItems);
             return "fragments/components/action-items-list :: content";

         } catch (Exception e) {
             log.error("Error fetching action items list: ", e);
             return "fragments/common-fragments :: error";
         }
     }

 }
