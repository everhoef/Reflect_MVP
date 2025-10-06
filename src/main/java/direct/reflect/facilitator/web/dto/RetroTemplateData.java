package direct.reflect.facilitator.web.dto;

import direct.reflect.facilitator.facilitation.RetroSession;
import direct.reflect.facilitator.facilitation.Participant;
import direct.reflect.facilitator.configurator.RetroStage;
import direct.reflect.facilitator.configurator.RetroStep;
import direct.reflect.facilitator.configurator.RetroTemplate;
import direct.reflect.facilitator.facilitation.RetroPhase;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Complete template data prepared by service layer for retro views
 */
@Data
@Builder
public class RetroTemplateData {
    // Basic page info
    private String page;
    private String title;
    private UUID retroId;
    
    // Session data
    private RetroSession session;
    private RetroTemplate template;
    private RetroStage currentStage;
    private RetroStep currentStep;
    
    // Participant data
    private Participant participant;
    private List<Participant> participants;
    private boolean isFacilitator;
    private String userName;
    
    // Phase data
    private RetroPhase currentPhase;
    
    // Processed step data
    private String stepGuidance;
    private Integer stepDurationMinutes;
    
    // Activity-specific data
    private ActivityTemplateData activityData;
    
    /**
     * Factory method for passive retro phases (lobby, paused, completed, abandoned)
     */
    public static RetroTemplateData forPassivePhase(UUID retroId, String title, RetroSession session, 
                                                   Participant participant, List<Participant> participants, boolean isFacilitator) {
        // Determine page based on phase - lobby uses lobby template, others use retro template
        String page = (session.getPhase() == RetroPhase.CREATED || session.getPhase() == RetroPhase.LOBBY) 
                     ? "lobby" : "retro";
                     
        return RetroTemplateData.builder()
                .page(page)
                .title(title)
                .retroId(retroId)
                .session(session)
                .template(session.getTemplate())
                .currentStage(session.getCurrentStage())
                .currentStep(null) // No active step in passive phases
                .currentPhase(session.getPhase())
                .participant(participant)
                .participants(participants)
                .isFacilitator(isFacilitator)
                .userName(participant.getDisplayName())
                .stepGuidance(null) // Templates will handle phase-appropriate messaging
                .stepDurationMinutes(null) // No timer in passive phases
                .activityData(null) // No activity data in passive phases
                .build();
    }
    
    /**
     * Factory method for active retro phases (set_the_stage, gather_data, generate_insights, decide_actions, close_retro)
     */
    public static RetroTemplateData forActivePhase(UUID retroId, String title, RetroSession session,
                                                  RetroTemplate template, RetroStage currentStage, RetroStep currentStep,
                                                  Participant participant, List<Participant> participants, boolean isFacilitator,
                                                  String stepGuidance, Integer stepDurationMinutes, ActivityTemplateData activityData) {
        return RetroTemplateData.builder()
                .page("retro")
                .title(title)
                .retroId(retroId)
                .session(session)
                .template(template)
                .currentStage(currentStage)
                .currentStep(currentStep)
                .currentPhase(session.getPhase())
                .participant(participant)
                .participants(participants)
                .isFacilitator(isFacilitator)
                .userName(participant.getDisplayName())
                .stepGuidance(stepGuidance)
                .stepDurationMinutes(stepDurationMinutes)
                .activityData(activityData)
                .build();
    }
}