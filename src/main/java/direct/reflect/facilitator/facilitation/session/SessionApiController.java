package direct.reflect.facilitator.facilitation.session;

import direct.reflect.facilitator.eventing.EventService;
import direct.reflect.facilitator.facilitation.participant.ParticipantService;
import direct.reflect.facilitator.facilitation.session.RetroSession;
import direct.reflect.facilitator.facilitation.session.RetroSessionService;
import direct.reflect.facilitator.facilitation.session.RetroSyncVersionService;
import direct.reflect.facilitator.facilitation.session.dto.AssistantStateDto;
import direct.reflect.facilitator.facilitation.session.dto.CreateRetroRequest;
import direct.reflect.facilitator.facilitation.session.dto.CreateRetroResponse;
import direct.reflect.facilitator.facilitation.session.dto.NextStepResult;
import direct.reflect.facilitator.facilitation.session.dto.TimerStateDto;
import direct.reflect.facilitator.facilitation.session.RetroPhase;
import direct.reflect.facilitator.facilitation.session.RetroSessionNotFoundException;
import direct.reflect.facilitator.facilitation.participant.Participant;
import direct.reflect.facilitator.facilitation.participant.ParticipantRole;
import direct.reflect.facilitator.facilitation.participant.ParticipantNotFoundException;
import direct.reflect.facilitator.facilitation.dto.RetroStateDto;
import direct.reflect.facilitator.facilitation.dto.StepSummaryDto;
import direct.reflect.facilitator.facilitation.dto.SyncVersionedResponse;
import direct.reflect.facilitator.configurator.RetroStep;
import direct.reflect.facilitator.configurator.RetroStage;
import direct.reflect.facilitator.configurator.RetroStepRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/retro")
@Tag(name = "Session API", description = "Retrospective session management")
@Slf4j
public class SessionApiController {
    private final RetroSessionService retroService;
    private final ParticipantService participantService;
    private final RetroStepRepository stepRepository;
    private final RetroSyncVersionService retroSyncVersionService;

    public SessionApiController(
            RetroSessionService retroService,
            ParticipantService participantService,
            RetroStepRepository stepRepository,
            RetroSyncVersionService retroSyncVersionService) {
        this.retroService = retroService;
        this.participantService = participantService;
        this.stepRepository = stepRepository;
        this.retroSyncVersionService = retroSyncVersionService;
    }

    @PostMapping(value = "/create", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('USER', 'GUEST')")
    public ResponseEntity<CreateRetroResponse> createRetrospective(
            @Valid @RequestBody CreateRetroRequest request,
            HttpServletRequest httpRequest,
            Authentication authentication) {
         
        log.debug("=== CREATE REQUEST START ===");
        log.debug("Request body: {}", request);
        log.debug("Authentication: {} (type: {})", authentication.getName(), authentication.getClass().getSimpleName());
        
        log.debug("Creating new retro session with name: {} by user: {}",
            request.sessionName(), authentication.getName());

        try {
            RetroSession retro = retroService.createSessionWithFacilitator(request.sessionName(), httpRequest);
            String redirectUrl = "/retro/" + retro.getId();

            log.info("✅ Created retro session: {} (id: {})", retro.getName(), retro.getId());

            return ResponseEntity.ok(new CreateRetroResponse(retro.getId(), redirectUrl, retro.getName()));

        } catch (Exception e) {
            log.error("❌ ERROR creating retro session", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{retroId}/start")
    @PreAuthorize("hasAnyRole('USER', 'GUEST')")
    @Operation(summary = "Start a retrospective session", description = "Transitions the session from LOBBY to active phase; facilitator-only action")
    @ApiResponse(responseCode = "200", description = "Session started successfully")
    @ApiResponse(responseCode = "403", description = "Only facilitators can start sessions")
    public ResponseEntity<Void> startSession(
            @PathVariable UUID retroId,
            HttpServletRequest httpRequest) {
        
        boolean isFacilitator = participantService.isFacilitator(httpRequest, retroId);
        if (!isFacilitator) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .header("HX-Redirect", "/retro/" + retroId)
                .build();
        }
        
        try {
            retroService.startSession(retroId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error starting session", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{retroId}/next")
    @PreAuthorize("hasAnyRole('USER', 'GUEST')")
    public ResponseEntity<NextStepResult> nextStep(
            @PathVariable UUID retroId,
            HttpServletRequest httpRequest) {

        boolean isFacilitator = participantService.isFacilitator(httpRequest, retroId);
        if (!isFacilitator) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            retroService.advanceToNextStep(retroId);

            return ResponseEntity.ok(new NextStepResult(retroId, true));
        } catch (Exception e) {
            log.error("Error advancing step", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{retroId}/timer")
    @PreAuthorize("hasAnyRole('USER', 'GUEST')")
    public ResponseEntity<SyncVersionedResponse<TimerStateDto>> getTimerState(
            @PathVariable UUID retroId,
            HttpServletRequest httpRequest) {
        
        log.debug("Getting timer state for retro: {}", retroId);
        
        try {
            participantService.getParticipantForSession(httpRequest, retroId);
        } catch (ParticipantNotFoundException e) {
            log.debug("Participant not found for retro: {}", retroId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        TimerStateDto state = retroService.getTimerState(retroId);
        if (state == null) {
            log.debug("No timer for current step in retro: {}", retroId);
            return ResponseEntity.noContent().build();
        }

        long syncVersion = retroSyncVersionService.getSyncVersion(retroId);
        log.debug("Returning timer state for retro: {} - remaining: {}s, paused: {}", 
            retroId, state.remainingSeconds(), state.isPaused());
        return ResponseEntity.ok(new SyncVersionedResponse<>(syncVersion, state));
    }

    @PostMapping("/{retroId}/timer/pause")
    @PreAuthorize("hasAnyRole('USER', 'GUEST')")
    @Operation(summary = "Pause the session timer", description = "Pauses the countdown timer for the current step; facilitator-only action")
    @ApiResponse(responseCode = "200", description = "Timer paused successfully")
    @ApiResponse(responseCode = "403", description = "Only facilitators can pause the timer")
    public ResponseEntity<Void> pauseTimer(
            @PathVariable UUID retroId,
            HttpServletRequest httpRequest) {
        
        log.debug("Pausing timer for retro: {}", retroId);
        
        if (!participantService.isFacilitator(httpRequest, retroId)) {
            log.debug("Non-facilitator attempted to pause timer for retro: {}", retroId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        try {
            retroService.pauseTimer(retroId);
            log.info("Paused timer for retro: {}", retroId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error pausing timer for retro: {}", retroId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{retroId}/timer/resume")
    @PreAuthorize("hasAnyRole('USER', 'GUEST')")
    @Operation(summary = "Resume the session timer", description = "Resumes the countdown timer for the current step; facilitator-only action")
    @ApiResponse(responseCode = "200", description = "Timer resumed successfully")
    @ApiResponse(responseCode = "403", description = "Only facilitators can resume the timer")
    public ResponseEntity<Void> resumeTimer(
            @PathVariable UUID retroId,
            HttpServletRequest httpRequest) {
        
        log.debug("Resuming timer for retro: {}", retroId);
        
        if (!participantService.isFacilitator(httpRequest, retroId)) {
            log.debug("Non-facilitator attempted to resume timer for retro: {}", retroId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        try {
            retroService.resumeTimer(retroId);
            log.info("Resumed timer for retro: {}", retroId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error resuming timer for retro: {}", retroId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{retroId}/state")
    @PreAuthorize("hasAnyRole('USER', 'GUEST')")
    public ResponseEntity<RetroStateDto> getRetroState(
            @PathVariable UUID retroId,
            HttpServletRequest httpRequest) {

        try {
            Participant currentParticipant = participantService.getParticipantForSession(httpRequest, retroId);
            RetroSession session = retroService.getSessionById(retroId);

            RetroPhase[] activePhases = {
                RetroPhase.SET_THE_STAGE,
                RetroPhase.GATHER_DATA,
                RetroPhase.GENERATE_INSIGHTS,
                RetroPhase.DECIDE_ACTIONS,
                RetroPhase.CLOSE_RETRO
            };

            List<StepSummaryDto> steps = new ArrayList<>();
            for (RetroPhase phase : activePhases) {
                RetroStage stage = session.getTemplate().getStageForPhase(phase);
                if (stage == null) continue;
                List<RetroStep> stageSteps = stepRepository.findByRetroStageOrderByOrderIndexAsc(stage);
                for (RetroStep step : stageSteps) {
                    steps.add(new StepSummaryDto(
                        step.getId(),
                        deriveTitleFromStep(step),
                        step.getComponentType().name(),
                        step.getAdvancementTrigger().name(),
                        step.getDurationSeconds() == 0 ? null : step.getDurationSeconds(),
                        step.getComponentConfig(),
                        step.getInstructions()
                    ));
                }
            }

            RetroStep currentStep = retroService.getCurrentStep(retroId);
            Long currentStepId = currentStep != null ? currentStep.getId() : null;

            List<Participant> activeParticipants = participantService.getSessionParticipants(retroId);
            UUID facilitatorId = activeParticipants.stream()
                .filter(p -> p.getRole() == ParticipantRole.FACILITATOR)
                .map(Participant::getParticipantId)
                .findFirst()
                .orElse(null);

            boolean isFacilitator = currentParticipant.getRole() == ParticipantRole.FACILITATOR;

            AssistantStateDto assistantState = retroService.getAssistantHistory(retroId);
            long syncVersion = retroSyncVersionService.getSyncVersion(retroId);

            RetroStateDto dto = new RetroStateDto(
                session.getId(),
                syncVersion,
                session.getPhase().name(),
                currentStepId,
                session.getCurrentStepIndex(),
                steps,
                facilitatorId,
                isFacilitator,
                activeParticipants.size(),
                assistantState
            );

            return ResponseEntity.ok(dto);

        } catch (ParticipantNotFoundException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (RetroSessionNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error getting retro state for {}: ", retroId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private String deriveTitleFromStep(RetroStep step) {
        Map<String, Object> config = step.getComponentConfig();
        if (config != null) {
            Object columns = config.get("columns");
            if (columns instanceof List<?> colList && !colList.isEmpty()) {
                Object first = colList.get(0);
                if (first instanceof Map<?, ?> colMap) {
                    Object title = colMap.get("title");
                    if (title instanceof String s && !s.isBlank()) {
                        return s;
                    }
                }
            }
        }
        return step.getComponentType().name().replace("_", " ");
    }
}
