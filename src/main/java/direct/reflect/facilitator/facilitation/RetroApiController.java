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
import direct.reflect.facilitator.facilitation.response.ResponseService;
import direct.reflect.facilitator.configurator.RetroStep;
import direct.reflect.facilitator.configurator.ComponentType;
import direct.reflect.facilitator.common.exception.RetroSessionNotFoundException;
import direct.reflect.facilitator.common.exception.VoteLimitExceededException;
import direct.reflect.facilitator.common.exception.ParticipantNotFoundException;
import direct.reflect.facilitator.common.exception.InputLimitExceededException;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;
import java.util.List;

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
    @PreAuthorize("hasAnyRole('USER', 'GUEST')") // Both users and guests can start sessions if they're facilitators
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
            // Service handles both database changes and event publishing within transaction
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
    
    /**
     * Submit a categorical response (MULTI_COLUMN_BOARD component type).
     * Form data is automatically validated using Jakarta Bean Validation annotations.
     */
    @PostMapping("/{retroId}/step/{stepId}/response/column")
    @PreAuthorize("hasAnyRole('USER', 'GUEST')")
    public ResponseEntity<String> submitColumnResponse(
            @PathVariable UUID retroId,
            @PathVariable Long stepId,
            @Valid @ModelAttribute ColumnResponseDto dto,
            HttpServletRequest httpRequest) {

        log.debug("Submitting column response for retro: {}, step: {}, column: {}",
            retroId, stepId, dto.columnId());

        try {
            responseService.submitResponse(retroId, stepId, dto, httpRequest);
            log.info("Submitted column response for step: {}", stepId);

            return ResponseEntity.ok()
                .header("HX-Trigger", "responseSubmitted")
                .body("");

        } catch (InputLimitExceededException e) {
            log.debug("Input limit exceeded for retro {}: {}", retroId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (RetroSessionNotFoundException e) {
            log.warn("Session not found: {}", retroId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error submitting column response: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Submit a rating response (RATING_SCALE component type).
     * Form data is automatically validated using Jakarta Bean Validation annotations.
     */
    @PostMapping("/{retroId}/step/{stepId}/response/rating")
    @PreAuthorize("hasAnyRole('USER', 'GUEST')")
    public ResponseEntity<Void> submitRatingResponse(
            @PathVariable UUID retroId,
            @PathVariable Long stepId,
            @Valid @ModelAttribute RatingResponseDto dto,
            HttpServletRequest httpRequest) {

        log.debug("Submitting rating response for retro: {}, step: {}, rating: {}",
            retroId, stepId, dto.rating());

        try {
            responseService.submitResponse(retroId, stepId, dto, httpRequest);
            log.info("Submitted rating response for step: {}", stepId);

            return ResponseEntity.ok()
                .header("HX-Trigger", "responseSubmitted")
                .build();

        } catch (RetroSessionNotFoundException e) {
            log.warn("Session not found: {}", retroId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error submitting rating response: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/{retroId}/response/{responseId}")
    @PreAuthorize("hasAnyRole('USER', 'GUEST')")
    public ResponseEntity<Void> updateResponse(
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

            responseService.updateResponse(responseId, participant, content);

            log.info("Updated response: {}", responseId);

            return ResponseEntity.ok().build();

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
    public ResponseEntity<String> toggleVote(
            @PathVariable UUID retroId,
            @PathVariable UUID responseId,
            HttpServletRequest httpRequest) {

        log.debug("Toggling vote for response {} in retro: {}", responseId, retroId);

        try {
            responseService.toggleVote(retroId, responseId, httpRequest);
            log.info("Toggled vote for response: {}", responseId);

            return ResponseEntity.ok()
                .header("HX-Trigger", "voteToggled")
                .build();

        } catch (VoteLimitExceededException e) {
            log.warn("Vote limit exceeded: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(e.getMessage());
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
     public ResponseEntity<Void> revealResponses(
             @PathVariable UUID retroId,
             @PathVariable Long stepId,
             HttpServletRequest httpRequest) {
         
         log.debug("Revealing responses for retro: {}, step: {}", retroId, stepId);
         
         try {
             // Only facilitators can reveal responses
             boolean isFacilitator = participantService.isFacilitator(httpRequest, retroId);
             if (!isFacilitator) {
                 return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
             }
             
             // Get the session
             RetroSession session = retroService.getSessionById(retroId);
             if (session == null) {
                 return ResponseEntity.notFound().build();
             }
             
             // Reveal all responses for this step
             responseService.revealAllResponses(session, stepId);
             
             log.info("Revealed responses for step: {} in retro: {}", stepId, retroId);
                 
             return ResponseEntity.ok()
                 .header("HX-Trigger", "responsesRevealed")
                 .build();
                 
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

 }