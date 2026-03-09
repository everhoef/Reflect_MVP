package direct.reflect.facilitator.facilitation;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import direct.reflect.facilitator.eventing.EventService;
import direct.reflect.facilitator.eventing.RetroEvent;
import direct.reflect.facilitator.facilitation.dto.CreateRetroRequest;
import direct.reflect.facilitator.facilitation.dto.JoinRetroRequest;
import direct.reflect.facilitator.facilitation.dto.SessionInfo;
import direct.reflect.facilitator.facilitation.dto.ComponentResponseDto;
import direct.reflect.facilitator.facilitation.dto.ColumnResponseDto;
import direct.reflect.facilitator.facilitation.dto.RatingResponseDto;
import direct.reflect.facilitator.facilitation.dto.TimerStateDto;
import direct.reflect.facilitator.facilitation.dto.SubmitResponseResult;
import direct.reflect.facilitator.facilitation.dto.VoteResult;
import direct.reflect.facilitator.facilitation.dto.RevealResult;
import direct.reflect.facilitator.facilitation.dto.UpdateResponseResult;
import direct.reflect.facilitator.facilitation.dto.RetroStateDto;
import direct.reflect.facilitator.facilitation.dto.StepSummaryDto;
import direct.reflect.facilitator.facilitation.dto.ParticipantDto;
import direct.reflect.facilitator.facilitation.response.ParticipantResponse;
import direct.reflect.facilitator.facilitation.response.ResponseService;
import direct.reflect.facilitator.configurator.RetroStep;
import direct.reflect.facilitator.configurator.RetroStage;
import direct.reflect.facilitator.configurator.RetroStepRepository;
import direct.reflect.facilitator.configurator.ComponentType;
import direct.reflect.facilitator.common.exception.RetroSessionNotFoundException;
import direct.reflect.facilitator.common.exception.VoteLimitExceededException;
import direct.reflect.facilitator.common.exception.ParticipantNotFoundException;
import direct.reflect.facilitator.common.exception.InputLimitExceededException;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/retro")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Retro API", description = "Retrospective session management and participant responses")
public class RetroApiController {
    private final RetroSessionService retroService;
    private final ParticipantService participantService;
    private final EventService eventService;
    private final ResponseService responseService;
    private final RetroStepRepository stepRepository;

    @PostMapping(value = "/create", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('USER', 'GUEST')")
    public ResponseEntity<Void> createRetrospective(
            @Valid @RequestBody CreateRetroRequest request,
            HttpServletRequest httpRequest,
            Authentication authentication) {
         
        log.debug("=== CREATE REQUEST START ===");
        log.debug("Request body: {}", request);
        log.debug("Authentication: {} (type: {})", authentication.getName(), authentication.getClass().getSimpleName());
        log.debug("Request method: {}", httpRequest.getMethod());
        log.debug("Request URI: {}", httpRequest.getRequestURI());
        
        log.debug("Creating new retro session with name: {} by user: {}",
            request.sessionName(), authentication.getName());

        try {
            // Service layer orchestrates the complete workflow
            RetroSession retro = retroService.createSessionWithFacilitator(request.sessionName(), httpRequest);
            String redirectUrl = "/retro/" + retro.getId();

            log.info("✅ Created retro session: {} (id: {})", retro.getName(), retro.getId());
            log.debug("Setting HX-Redirect header to: {}", redirectUrl);
            log.debug("Returning response with status: {}", HttpStatus.FOUND);

            ResponseEntity<Void> response = ResponseEntity.status(HttpStatus.FOUND)
                .header("HX-Redirect", redirectUrl)
                .build();

            log.debug("=== CREATE REQUEST SUCCESS - Response built with HX-Redirect: {} ===", redirectUrl);
            return response;

        } catch (Exception e) {
            log.error("❌ ERROR creating retro session", e);
            log.error("Exception type: {}, message: {}", e.getClass().getName(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("HX-Redirect", "/home?error=creation_failed")
                .build();
        }
    }

    @PostMapping(value = "/join", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('USER', 'GUEST')")
    public ResponseEntity<Void> joinRetrospective(
            @Valid @RequestBody JoinRetroRequest request,
            HttpServletRequest httpRequest,
            Authentication authentication) {
        
        log.debug("=== JOIN REQUEST START ===");
        log.debug("Request retroId: {}", request.retroId());
        log.debug("Authentication: {} (type: {})", authentication.getName(), authentication.getClass().getSimpleName());
        log.debug("Request path: {}", httpRequest.getRequestURI());
        
        
        UUID retroId;
        try {
            retroId = UUID.fromString(request.retroId());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID format for retroId: {}", request.retroId());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .header("HX-Redirect", "/home?error=invalid_input")
                .build();
        }
        
        log.debug("Join request for retro: {} by user: {}", 
            retroId, authentication.getName());
        
        try {
            // Service layer handles all business logic including event publishing
            RetroSession sessionToJoin = retroService.getSessionById(retroId);
            participantService.addParticipantToSession(httpRequest, sessionToJoin, ParticipantRole.PARTICIPANT);
            
            log.debug("Successfully added participant to session, redirecting to: /retro/{}", retroId);
            
            return ResponseEntity.status(HttpStatus.FOUND)
                .header("HX-Redirect", "/retro/" + retroId)
                .build();
            
        } catch (RetroSessionNotFoundException e) {
            log.warn("Session not found: {}", retroId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .header("HX-Redirect", "/home?error=session_not_found")
                .build();
        } catch (Exception e) {
            log.error("Error joining retro session {}: ", retroId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("HX-Redirect", "/home?error=join_failed")
                .build();
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
    @PreAuthorize("hasAnyRole('USER', 'GUEST')") // Both users and guests can advance sessions if they're facilitators
    public ResponseEntity<Void> nextStep(
            @PathVariable UUID retroId,
            HttpServletRequest httpRequest) {

        boolean isFacilitator = participantService.isFacilitator(httpRequest, retroId);
        if (!isFacilitator) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .header("HX-Redirect", "/retro/" + retroId)
                .build();
        }

        try {
            // Service handles both database changes and event publishing within transaction
            retroService.advanceToNextStep(retroId);

            return ResponseEntity.ok()
                .header("HX-Trigger", "stepAdvanced")
                .build();
        } catch (Exception e) {
            log.error("Error advancing step", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("HX-Redirect", "/retro/" + retroId + "?error=next_step_failed")
                .build();
        }
    }
    
    @PostMapping("/leave-active-sessions")
    @PreAuthorize("hasAnyRole('USER', 'GUEST')")
    public ResponseEntity<Void> leaveActiveSessions(HttpServletRequest httpRequest) {
        log.debug("Request to leave all active sessions");
        
        try {
            participantService.leaveAllActiveSessions(httpRequest);
            log.info("Successfully left all active sessions");
            return ResponseEntity.status(HttpStatus.FOUND)
                .header("HX-Redirect", "/home?message=left_sessions")
                .build();
        } catch (Exception e) {
            log.error("Error leaving active sessions: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("HX-Redirect", "/home?error=leave_sessions_failed")
                .build();
        }
    }
    
    @GetMapping("/check-active-sessions")
    @PreAuthorize("hasAnyRole('USER', 'GUEST')")
    public ResponseEntity<List<SessionInfo>> checkActiveSessions(HttpServletRequest httpRequest) {
        try {
            // Get participant ID from session attributes (much simpler!)
            HttpSession session = httpRequest.getSession(false);
            if (session == null) {
                return ResponseEntity.ok(List.of());
            }
            
            UUID participantId = (UUID) session.getAttribute("participantId");
            if (participantId == null) {
                return ResponseEntity.ok(List.of());
            }
            
            List<Participant> activeSessions = participantService.getActiveSessionsForParticipant(participantId);
            
            List<SessionInfo> sessionInfos = activeSessions.stream()
                .map(p -> new SessionInfo(
                    p.getSession().getId(),
                    p.getSession().getName(),
                    p.getRole().name()
                ))
                .toList();
            
            return ResponseEntity.ok(sessionInfos);
            
        } catch (Exception e) {
            log.error("Error checking active sessions: ", e);
            return ResponseEntity.ok(List.of());
        }
    }
    
    @PostMapping("/{retroId}/step/{stepId}/response/column")
    @PreAuthorize("hasAnyRole('USER', 'GUEST')")
    @Operation(summary = "Submit a column/categorical response", description = "Submits a response to a multi-column board step")
    @ApiResponse(responseCode = "200", description = "Response submitted successfully")
    @ApiResponse(responseCode = "400", description = "Validation error or input limit exceeded")
    public ResponseEntity<SubmitResponseResult> submitColumnResponse(
            @PathVariable UUID retroId,
            @PathVariable Long stepId,
            @Valid @ModelAttribute ColumnResponseDto dto,
            HttpServletRequest httpRequest) {

        log.debug("Submitting column response for retro: {}, step: {}, column: {}",
            retroId, stepId, dto.columnId());

        try {
            ParticipantResponse saved = responseService.submitResponse(retroId, stepId, dto, httpRequest);
            log.info("Submitted column response for step: {}", stepId);

            return ResponseEntity.ok()
                .header("HX-Trigger", "responseSubmitted")
                .body(new SubmitResponseResult(saved.getId(), stepId));

        } catch (InputLimitExceededException e) {
            log.debug("Input limit exceeded for retro {}: {}", retroId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (RetroSessionNotFoundException e) {
            log.warn("Session not found: {}", retroId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error submitting column response: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{retroId}/step/{stepId}/response/rating")
    @PreAuthorize("hasAnyRole('USER', 'GUEST')")
    @Operation(summary = "Submit a rating response", description = "Submits a rating scale response for the current step")
    @ApiResponse(responseCode = "200", description = "Rating submitted successfully")
    @ApiResponse(responseCode = "400", description = "Validation error")
    public ResponseEntity<SubmitResponseResult> submitRatingResponse(
            @PathVariable UUID retroId,
            @PathVariable Long stepId,
            @Valid @ModelAttribute RatingResponseDto dto,
            HttpServletRequest httpRequest) {

        log.debug("Submitting rating response for retro: {}, step: {}, rating: {}",
            retroId, stepId, dto.rating());

        try {
            ParticipantResponse saved = responseService.submitResponse(retroId, stepId, dto, httpRequest);
            log.info("Submitted rating response for step: {}", stepId);

            return ResponseEntity.ok()
                .header("HX-Trigger", "responseSubmitted")
                .body(new SubmitResponseResult(saved.getId(), stepId));

        } catch (RetroSessionNotFoundException e) {
            log.warn("Session not found: {}", retroId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error submitting rating response: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{retroId}/step/{stepId}/response/rating")
    @PreAuthorize("hasAnyRole('USER', 'GUEST')")
    @Operation(summary = "Get rating responses for histogram", description = "Returns all rating responses for the stage containing this step (used by HISTOGRAM_CHART component)")
    @ApiResponse(responseCode = "200", description = "Rating responses returned")
    public ResponseEntity<List<RatingResponseDto>> getRatingResponses(
            @PathVariable UUID retroId,
            @PathVariable Long stepId,
            HttpServletRequest httpRequest) {

        try {
            participantService.getParticipantForSession(httpRequest, retroId);
            RetroSession session = retroService.getSessionById(retroId);

            RetroStep step = stepRepository.findById(stepId)
                .orElseThrow(() -> new IllegalArgumentException("Step not found: " + stepId));
            RetroStage stage = step.getRetroStage();

            List<RatingResponseDto> dtos = responseService
                .getResponsesForStageComponentType(session, stage, ComponentType.RATING_SCALE)
                .stream()
                .map(RatingResponseDto::from)
                .toList();

            return ResponseEntity.ok(dtos);

        } catch (ParticipantNotFoundException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (RetroSessionNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error fetching rating responses for retro {}, step {}: ", retroId, stepId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/{retroId}/response/{responseId}")
    @PreAuthorize("hasAnyRole('USER', 'GUEST')")
    @Operation(summary = "Update a response", description = "Updates the content of an existing participant response")
    @ApiResponse(responseCode = "200", description = "Response updated successfully")
    @ApiResponse(responseCode = "403", description = "Not authorized to edit this response")
    public ResponseEntity<UpdateResponseResult> updateResponse(
            @PathVariable UUID retroId,
            @PathVariable UUID responseId,
            @RequestParam String content,
            HttpServletRequest httpRequest) {

        log.debug("Updating response {} for retro: {}", responseId, retroId);

        try {
            Participant participant = participantService.getParticipantForSession(httpRequest, retroId);
            if (participant == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            ParticipantResponse saved = responseService.updateResponse(responseId, participant, content);

            log.info("Updated response: {}", responseId);

            return ResponseEntity.ok(new UpdateResponseResult(saved.getId(), content));

        } catch (SecurityException e) {
            log.warn("Unauthorized response update attempt: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            log.error("Error updating response: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{retroId}/response/{responseId}/vote")
    @PreAuthorize("hasAnyRole('USER', 'GUEST')")
    @Operation(summary = "Toggle a vote on a response", description = "Adds or removes a vote for the current participant on the specified response")
    @ApiResponse(responseCode = "200", description = "Vote toggled successfully, returns updated vote count")
    @ApiResponse(responseCode = "400", description = "Vote limit exceeded")
    public ResponseEntity<VoteResult> toggleVote(
            @PathVariable UUID retroId,
            @PathVariable UUID responseId,
            HttpServletRequest httpRequest) {

        log.debug("Toggling vote for response {} in retro: {}", responseId, retroId);

        try {
            ParticipantResponse saved = responseService.toggleVote(retroId, responseId, httpRequest);
            log.info("Toggled vote for response: {}", responseId);

            Object votesObj = saved.getResponseData().get("votes");
            int voteCount = 0;
            if (votesObj instanceof List<?>) {
                voteCount = ((List<?>) votesObj).size();
            }

            return ResponseEntity.ok()
                .header("HX-Trigger", "voteToggled")
                .body(new VoteResult(responseId, voteCount));

        } catch (VoteLimitExceededException e) {
            log.warn("Vote limit exceeded: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (SecurityException e) {
            log.warn("Unauthorized vote attempt: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            log.error("Error toggling vote: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

     @PostMapping("/{retroId}/step/{stepId}/reveal")
     @PreAuthorize("hasAnyRole('USER', 'GUEST')")
     @Operation(summary = "Reveal responses for a step", description = "Makes all participant responses visible; facilitator-only action")
     @ApiResponse(responseCode = "200", description = "Responses revealed successfully")
     @ApiResponse(responseCode = "403", description = "Only facilitators can reveal responses")
     public ResponseEntity<RevealResult> revealResponses(
             @PathVariable UUID retroId,
             @PathVariable Long stepId,
             HttpServletRequest httpRequest) {
         
         log.debug("Revealing responses for retro: {}, step: {}", retroId, stepId);
         
         try {
             boolean isFacilitator = participantService.isFacilitator(httpRequest, retroId);
             if (!isFacilitator) {
                 return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
             }
             
             RetroSession session = retroService.getSessionById(retroId);
             if (session == null) {
                 return ResponseEntity.notFound().build();
             }
             
             responseService.revealAllResponses(session, stepId);
             
             log.info("Revealed responses for step: {} in retro: {}", stepId, retroId);
                 
             return ResponseEntity.ok()
                 .header("HX-Trigger", "responsesRevealed")
                 .body(new RevealResult(stepId, true));
                 
         } catch (Exception e) {
             log.error("Error revealing responses: ", e);
             return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
         }
     }

     @GetMapping("/{retroId}/timer")
     @PreAuthorize("hasAnyRole('USER', 'GUEST')")
     public ResponseEntity<TimerStateDto> getTimerState(
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
         
         log.debug("Returning timer state for retro: {} - remaining: {}s, paused: {}", 
             retroId, state.remainingSeconds(), state.isPaused());
         return ResponseEntity.ok(state);
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

            RetroStateDto dto = new RetroStateDto(
                session.getId(),
                session.getPhase().name(),
                currentStepId,
                session.getCurrentStepIndex(),
                steps,
                facilitatorId,
                isFacilitator,
                activeParticipants.size()
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

    @GetMapping("/{retroId}/participants")
    @PreAuthorize("hasAnyRole('USER', 'GUEST')")
    public ResponseEntity<List<ParticipantDto>> getParticipants(
            @PathVariable UUID retroId,
            HttpServletRequest httpRequest) {

        try {
            participantService.getParticipantForSession(httpRequest, retroId);

            List<ParticipantDto> participants = participantService.getSessionParticipants(retroId)
                .stream()
                .map(p -> new ParticipantDto(
                    p.getParticipantId(),
                    p.getDisplayName(),
                    p.getRole().name()
                ))
                .toList();

            return ResponseEntity.ok(participants);

        } catch (ParticipantNotFoundException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            log.error("Error getting participants for {}: ", retroId, e);
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