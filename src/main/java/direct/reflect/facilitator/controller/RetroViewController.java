package direct.reflect.facilitator.controller;

import java.util.Map;
import java.util.UUID;
import java.util.HashMap;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import direct.reflect.facilitator.service.RetroSessionService;
import direct.reflect.facilitator.service.ParticipantService;
import direct.reflect.facilitator.domain.entity.RetroSession;
import direct.reflect.facilitator.domain.entity.RetroStep;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequiredArgsConstructor
@Slf4j
public class RetroViewController {
    private final RetroSessionService retroService;
    private final ParticipantService participantService;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("page", "home");  // tells layout.html which fragment to load
        model.addAttribute("title", "Reflect.Direct Facilitator");
        model.addAttribute("templates", retroService.getAvailableTemplates());
        return "layout";  // always returns the main layout template
    }

    @GetMapping("/retrospective/{retroId}")
    public String retrospectiveView(@PathVariable UUID retroId, Model model) {
        if (!participantService.isParticipating(retroId)) {
            return "redirect:/?error=not_participant";
        }

        RetroSession session = retroService.getSessionByRetroId(retroId);
        RetroStep currentStep = retroService.getCurrentStep(retroId);
        
        // Common attributes for all phases
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("retroId", retroId);
        attributes.put("title", session.getName());
        attributes.put("session", session);
        attributes.put("isFacilitator", participantService.isFacilitator(retroId));
        attributes.put("userName", participantService.getCurrentParticipant().getUsername());
        
        // Phase-specific attributes and view selection
        switch(session.getPhase()) {
            case LOBBY -> {
                attributes.put("page", "lobby");
                attributes.put("participants", participantService.getSessionParticipants(retroId));
            }
            case SET_THE_STAGE, GATHER_DATA, GENERATE_INSIGHTS, DECIDE_ACTIONS, CLOSE_RETRO -> {
                attributes.put("page", "retro");
                attributes.put("currentPhase", session.getPhase());
                attributes.put("currentStage", session.getCurrentStage());
                attributes.put("currentStep", currentStep);
                attributes.put("template", session.getTemplate());
            }
            case COMPLETED -> {
                attributes.put("page", "completed");
                // Add any completion-specific attributes
            }
            default -> {
                return "redirect:/?error=invalid_phase";
            }
        }

        model.addAllAttributes(attributes);
        return "layout";
    }

    // Fragment-only views (no layout)
    @GetMapping("/retrospective/{retroId}/participants")
    public String getParticipantsList(@PathVariable UUID retroId, Model model) {
        model.addAttribute("participants", participantService.getSessionParticipants(retroId));
        return "fragments/lobby :: ul.space-y-2";  // Direct fragment reference
    }
}
