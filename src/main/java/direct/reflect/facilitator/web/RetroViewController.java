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
import direct.reflect.facilitator.auth.AuthenticationHelper;
import direct.reflect.facilitator.web.RetroTemplateDataService;
import direct.reflect.facilitator.web.dto.RetroTemplateData;
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
    private final AuthenticationHelper authenticationHelper;
    private final RetroTemplateDataService templateDataService;

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
            RetroTemplateData templateData = templateDataService.prepareRetroViewData(retroId, request);
            
            // Add the single DTO to model - templates will use templateData.*
            model.addAttribute("templateData", templateData);
            
            log.info("Prepared template data for retro {} - page: {}, phase: {}", 
                retroId, templateData.getPage(), templateData.getCurrentPhase());
            
            return "layout";
            
        } catch (ParticipantNotFoundException e) {
            log.error("Error in getParticipantForSession chain: ", e);
            return "redirect:/login";
        } catch (Exception e) {
            log.error("Error in retroView: ", e);
            throw e;
        }
    }

    @GetMapping("/retro/{retroId}/participants")
    public String getParticipantsList(@PathVariable UUID retroId, Model model, HttpServletRequest request) {
        log.debug("Getting participants list for retro session: {}", retroId);
        
        try {
            RetroTemplateData templateData = templateDataService.prepareRetroViewData(retroId, request);
            model.addAttribute("templateData", templateData);
            return "fragments/participants :: participantsList";
            
        } catch (ParticipantNotFoundException e) {
            log.error("Participant not found when getting participants list: ", e);
            // Return empty list for non-participants
            model.addAttribute("participants", java.util.Collections.emptyList());
            return "fragments/participants :: participantsList";
        }
    }
}
