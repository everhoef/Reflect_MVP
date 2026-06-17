package direct.reflect.facilitator.facilitation.session;

import direct.reflect.facilitator.facilitation.participant.ParticipantService;
import direct.reflect.facilitator.facilitation.session.dto.AssistantStateDto;
import direct.reflect.facilitator.facilitation.session.dto.CreateRetroRequest;
import direct.reflect.facilitator.facilitation.session.dto.CreateRetroResponse;
import direct.reflect.facilitator.facilitation.session.dto.NextStepResult;
import direct.reflect.facilitator.facilitation.session.dto.TimerStateDto;
import direct.reflect.facilitator.facilitation.participant.Participant;
import direct.reflect.facilitator.facilitation.participant.ParticipantRole;
import direct.reflect.facilitator.facilitation.dto.RetroStateDto;
import direct.reflect.facilitator.facilitation.dto.StepSummaryDto;
import direct.reflect.facilitator.facilitation.dto.SyncVersionedResponse;
import direct.reflect.facilitator.configurator.RetroStep;
import direct.reflect.facilitator.configurator.RetroStage;
import direct.reflect.facilitator.configurator.RetroStepQueryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/retros")
@Tag(name = "Session API", description = "Retrospective session management")
@Slf4j
@RequiredArgsConstructor
public class SessionApiController {
    private final RetroSessionService retroService;
    private final ParticipantService participantService;
    private final RetroStepQueryService retroStepQueryService;
    private final RetroSyncVersionService retroSyncVersionService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('USER', 'GUEST')")
    @Operation(summary = "Create a retrospective session",
        description = "Creates a new retrospective session and registers the creator as facilitator")
    @ApiResponse(responseCode = "200", description = "Session created successfully")
    @ApiResponse(responseCode = "400", description = "Validation error")
    @ApiResponse(responseCode = "500", description = "Session could not be created")
    public ResponseEntity<CreateRetroResponse> createRetrospective(
            @Valid @RequestBody CreateRetroRequest request,
            HttpServletRequest httpRequest,
            Authentication authentication) {

        log.debug("Creating retro session '{}' for {}",
            request.sessionName(), authentication.getName());

        try {
            RetroSession retro = retroService.createSessionWithFacilitator(request.sessionName(), httpRequest);
            String redirectUrl = "/retro/" + retro.getId();

            log.info("Created retro session '{}' ({})", retro.getName(), retro.getId());

            return ResponseEntity.ok(new CreateRetroResponse(retro.getId(), redirectUrl, retro.getName()));

        } catch (Exception e) {
            log.error("Error creating retro session '{}'", request.sessionName(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{retroId}/start")
    @PreAuthorize("hasAnyRole('USER', 'GUEST')")
    @Operation(summary = "Start a retrospective session",
        description = "Transitions the session from LOBBY to active phase; facilitator-only action")
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

    @PostMapping("/{retroId}/advance")
    @PreAuthorize("hasAnyRole('USER', 'GUEST')")
    @Operation(summary = "Advance to the next step", description = "Advances the retrospective to the next step; facilitator-only action")
    @ApiResponse(responseCode = "200", description = "Advanced to the next step")
    @ApiResponse(responseCode = "403", description = "Only facilitators can advance the session")
    public ResponseEntity<NextStepResult> nextStep(
            @PathVariable UUID retroId,
            HttpServletRequest httpRequest) {

        if (!hasFacilitatorAccess(httpRequest, retroId)) {
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
    @Operation(summary = "Get timer state", description = "Returns the timer state for the current step")
    @ApiResponse(responseCode = "200", description = "Timer state returned")
    @ApiResponse(responseCode = "204", description = "Current step has no timer")
    @ApiResponse(responseCode = "403", description = "Participant is not part of this session")
    public ResponseEntity<SyncVersionedResponse<TimerStateDto>> getTimerState(
            @PathVariable UUID retroId,
            HttpServletRequest httpRequest) {

        if (!hasParticipantAccess(httpRequest, retroId)) {
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
    @Operation(summary = "Pause the session timer",
        description = "Pauses the countdown timer for the current step; facilitator-only action")
    @ApiResponse(responseCode = "200", description = "Timer paused successfully")
    @ApiResponse(responseCode = "403", description = "Only facilitators can pause the timer")
    public ResponseEntity<Void> pauseTimer(
            @PathVariable UUID retroId,
            HttpServletRequest httpRequest) {

        log.debug("Pausing timer for retro: {}", retroId);

        if (!hasFacilitatorAccess(httpRequest, retroId)) {
            log.debug("Non-facilitator attempted to pause timer for retro: {}", retroId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            retroService.pauseTimer(retroId);
            log.debug("Paused timer for retro: {}", retroId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error pausing timer for retro: {}", retroId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{retroId}/timer/resume")
    @PreAuthorize("hasAnyRole('USER', 'GUEST')")
    @Operation(summary = "Resume the session timer",
        description = "Resumes the countdown timer for the current step; facilitator-only action")
    @ApiResponse(responseCode = "200", description = "Timer resumed successfully")
    @ApiResponse(responseCode = "403", description = "Only facilitators can resume the timer")
    public ResponseEntity<Void> resumeTimer(
            @PathVariable UUID retroId,
            HttpServletRequest httpRequest) {

        log.debug("Resuming timer for retro: {}", retroId);

        if (!hasFacilitatorAccess(httpRequest, retroId)) {
            log.debug("Non-facilitator attempted to resume timer for retro: {}", retroId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            retroService.resumeTimer(retroId);
            log.debug("Resumed timer for retro: {}", retroId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error resuming timer for retro: {}", retroId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{retroId}")
    @PreAuthorize("hasAnyRole('USER', 'GUEST')")
    @Operation(summary = "Get retrospective state", description = "Returns the current retrospective state for an active participant")
    @ApiResponse(responseCode = "200", description = "Retrospective state returned")
    @ApiResponse(responseCode = "403", description = "Participant is not part of this session")
    @ApiResponse(responseCode = "404", description = "Session not found")
    public ResponseEntity<RetroStateDto> getRetroState(
            @PathVariable UUID retroId,
            HttpServletRequest httpRequest) {

        if (!hasParticipantAccess(httpRequest, retroId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

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
                RetroStage stage = session.getStageForPhase(phase);
                if (stage == null) {
                    continue;
                }
                List<RetroStep> stageSteps = retroStepQueryService.findStepsByStage(stage);
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

        } catch (RetroSessionNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error getting retro state for {}: ", retroId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private boolean hasParticipantAccess(HttpServletRequest request, UUID retroId) {
        return participantService.isParticipating(request, retroId);
    }

    private boolean hasFacilitatorAccess(HttpServletRequest request, UUID retroId) {
        return participantService.isFacilitator(request, retroId);
    }

    private String deriveTitleFromStep(RetroStep step) {
        if (step.getRetroStage() != null && step.getRetroStage().getName() != null
                && !step.getRetroStage().getName().isBlank()) {
            return step.getRetroStage().getName();
        }
        return step.getComponentType().name().replace("_", " ");
    }
}
