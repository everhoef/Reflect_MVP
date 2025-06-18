package direct.reflect.facilitator.controller;

import java.util.Map;
import java.util.UUID;
import java.util.HashMap;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.reactive.result.view.Rendering;
import org.springframework.web.server.ServerWebExchange;

import direct.reflect.facilitator.service.RetroSessionService;
import direct.reflect.facilitator.service.ParticipantService;
import direct.reflect.facilitator.domain.entity.RetroSession;
import direct.reflect.facilitator.domain.entity.RetroStep;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Controller
@RequiredArgsConstructor
@Slf4j
public class RetroViewController {
    private final RetroSessionService retroService;
    private final ParticipantService participantService;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("page", "home");
        model.addAttribute("title", "Reflect.Direct Facilitator");
        model.addAttribute("templates", retroService.getAvailableTemplates());
        return "layout";
    }

    @GetMapping("/retro/{retroId}")
    public Mono<Rendering> retroView(
            @PathVariable UUID retroId,
            ServerWebExchange exchange) {
        
        // Check if user is participating
        return participantService.isParticipating(exchange, retroId)
            .flatMap(isParticipating -> {
                if (!isParticipating) {
                    return Mono.just(Rendering.redirectTo("/?error=not_participant").build());
                }

                RetroSession session = retroService.getSessionById(retroId);
                RetroStep currentStep = retroService.getCurrentStep(retroId);
                
                // Get current participant
                return participantService.getCurrentParticipant(exchange, null)
                    .flatMap(participant -> {
                        // Common attributes for all phases
                        Map<String, Object> attributes = new HashMap<>();
                        attributes.put("retroId", retroId);
                        attributes.put("title", session.getName());
                        attributes.put("session", session);
                        attributes.put("participant", participant);
                        attributes.put("userName", participant.getDisplayName());
                        
                        // Phase-specific attributes
                        return participantService.isFacilitator(exchange, retroId)
                            .map(isFacilitator -> {
                                attributes.put("isFacilitator", isFacilitator);
                                
                                // Set page based on phase
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
                                    }
                                    default -> {
                                        return Rendering.redirectTo("/?error=invalid_phase").build();
                                    }
                                }
                                
                                return Rendering.view("layout")
                                    .modelAttributes(attributes)
                                    .build();
                            });
                    })
                    .defaultIfEmpty(Rendering.redirectTo("/?error=participant_not_found").build());
            });
    }

    @GetMapping("/retro/{retroId}/participants")
    public String getParticipantsList(@PathVariable UUID retroId, Model model) {
        model.addAttribute("participants", participantService.getSessionParticipants(retroId));
        return "fragments/lobby :: ul.space-y-2";  // Direct fragment reference
    }
}
