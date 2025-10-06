package direct.reflect.facilitator.web;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import direct.reflect.facilitator.facilitation.RetroSessionService;
import direct.reflect.facilitator.facilitation.ParticipantService;
import direct.reflect.facilitator.facilitation.RetroSession;
import direct.reflect.facilitator.facilitation.Participant;
import direct.reflect.facilitator.configurator.RetroStage;
import direct.reflect.facilitator.configurator.RetroStep;
import direct.reflect.facilitator.configurator.RetroTemplate;
import direct.reflect.facilitator.web.dto.RetroTemplateData;
import direct.reflect.facilitator.web.dto.ActivityTemplateData;

import java.util.UUID;
import java.util.List;

/**
 * Service responsible for preparing template data for retro views.
 * This separates presentation logic from domain logic.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RetroTemplateDataService {
    
    private final RetroSessionService retroSessionService;
    private final ParticipantService participantService;
    private final ObjectMapper objectMapper;
    
    /**
     * Main method to prepare complete template data for retro views.
     * Centralizes all template data preparation logic previously in controller.
     */
    public RetroTemplateData prepareRetroViewData(UUID retroId, HttpServletRequest request) {
        log.debug("Preparing template data for retro {}", retroId);
        
        // Always get these core domain objects for all phases
        RetroSession session = retroSessionService.getSessionById(retroId);
        Participant participant = participantService.getParticipantForSession(request, retroId);
        boolean isFacilitator = participantService.isFacilitator(request, retroId);
        List<Participant> participants = participantService.getSessionParticipants(retroId);
        
        return switch (session.getPhase()) {
            case CREATED, LOBBY, PAUSED, COMPLETED, ABANDONED -> 
                RetroTemplateData.forPassivePhase(
                    retroId, session.getName(), session, participant, participants, isFacilitator);
                    
            case SET_THE_STAGE, GATHER_DATA, GENERATE_INSIGHTS, DECIDE_ACTIONS, CLOSE_RETRO -> {
                // ACTIVE phases: get full step data
                RetroStep currentStep = retroSessionService.getCurrentStep(retroId);
                String stepGuidance = retroSessionService.getStepGuidanceContent(currentStep);
                Integer stepDurationMinutes = calculateStepDurationMinutes(currentStep);
                ActivityTemplateData activityData = parseActivityData(currentStep);
                
                yield RetroTemplateData.forActivePhase(
                    retroId, session.getName(), session, session.getTemplate(), session.getCurrentStage(),
                    currentStep, participant, participants, isFacilitator, 
                    stepGuidance, stepDurationMinutes, activityData);
            }
        };
    }
    
    
    /**
     * Calculate step duration in minutes for display
     */
    private Integer calculateStepDurationMinutes(RetroStep step) {
        if (step == null || step.getDurationSeconds() == null || step.getDurationSeconds() <= 0) {
            return null;
        }
        return step.getDurationSeconds() / 60;
    }
    
    /**
     * Parse activity-specific configuration from RetroStep JSON
     */
    private ActivityTemplateData parseActivityData(RetroStep step) {
        if (step == null || step.getDataPattern() == null) {
            return null;
        }
        
        try {
            JsonNode config = objectMapper.readTree(step.getConfiguration());
            
            return switch (step.getDataPattern()) {
                case CATEGORICAL -> parseCategoricalData(config);
                case RATING -> parseRatingData(config);
                case FREEFORM -> parseFreeformData(config);
                default -> {
                    log.debug("Unknown data pattern: {} for step {}", step.getDataPattern(), step.getId());
                    yield null;
                }
            };
        } catch (Exception e) {
            log.warn("Failed to parse activity configuration for step {}: {}", step.getId(), e.getMessage());
            return null;
        }
    }
    
    /**
     * Parse categorical activity configuration (Mad/Sad/Glad, etc.)
     */
    private ActivityTemplateData parseCategoricalData(JsonNode config) {
        try {
            // For now, return simple structure - can be expanded later
            log.debug("Parsing categorical data: {}", config);
            
            // TODO: Parse categories array, colors, etc. from JSON
            // This is where we'd extract the complex logic from retro-categorical.html
            
            return ActivityTemplateData.categorical(
                ActivityTemplateData.CategoricalData.builder()
                    .maxLength(config.path("maxLength").asInt(200))
                    .allowMultiple(config.path("allowMultiple").asBoolean(false))
                    .build()
            );
        } catch (Exception e) {
            log.warn("Failed to parse categorical data: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Parse rating activity configuration (scales, labels, etc.)
     */
    private ActivityTemplateData parseRatingData(JsonNode config) {
        try {
            log.debug("Parsing rating data: {}", config);
            
            // TODO: Parse scale min/max/step, labels array, etc.
            
            return ActivityTemplateData.rating(
                ActivityTemplateData.RatingData.builder()
                    .allowComment(config.path("allowComment").asBoolean(true))
                    .build()
            );
        } catch (Exception e) {
            log.warn("Failed to parse rating data: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Parse freeform activity configuration (prompts, limits, etc.)
     */
    private ActivityTemplateData parseFreeformData(JsonNode config) {
        try {
            log.debug("Parsing freeform data: {}", config);
            
            return ActivityTemplateData.freeform(
                ActivityTemplateData.FreeformData.builder()
                    .prompt(config.path("prompt").asText("Share your thoughts"))
                    .maxLength(config.path("maxLength").asInt(500))
                    .allowMultiple(config.path("allowMultiple").asBoolean(false))
                    .build()
            );
        } catch (Exception e) {
            log.warn("Failed to parse freeform data: {}", e.getMessage());
            return null;
        }
    }
}