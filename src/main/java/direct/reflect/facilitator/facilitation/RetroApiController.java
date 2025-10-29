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
import direct.reflect.facilitator.facilitation.response.ResponseService;
import direct.reflect.facilitator.configurator.DataPattern;
import direct.reflect.facilitator.configurator.RetroStep;
import direct.reflect.facilitator.common.exception.RetroSessionNotFoundException;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/retro")
@RequiredArgsConstructor
@Slf4j
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
            // Display name is derived from the authentication context (no need to pass it)
            Participant participant = participantService.createAndAssignFacilitatorForSession(request.sessionName(), httpRequest);
            
            // The participant object now holds the session reference
            RetroSession retro = participant.getSession();
            log.info("Created new retro session: {} (id: {}) by facilitator: {}", 
                retro.getName(), retro.getId(), participant.getDisplayName());
            
            // Publish retro created event
            try {
                eventService.publish(RetroEvent.retroCreated(retro.getId(), "system"));
            } catch (Exception eventError) {
                log.error("Failed to publish retro created event: {}", eventError.getMessage());
                // Continue anyway - event publishing failure shouldn't block user flow
            }
            
            return ResponseEntity.status(HttpStatus.FOUND)
                .header("HX-Redirect", "/retro/" + retro.getId())
                .build();
            
        } catch (Exception e) {
            log.error("Error creating retro session: ", e);
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
            retroService.startSession(retroId);
            
            // Publish session started event
            try {
                eventService.publish(RetroEvent.sessionStarted(retroId));
            } catch (Exception eventError) {
                log.error("Failed to publish session started event: {}", eventError.getMessage());
                // Continue anyway - event publishing failure shouldn't block user flow
            }
            
            return ResponseEntity.status(HttpStatus.FOUND)
                .header("HX-Redirect", "/retro/" + retroId)
                .build();
        } catch (Exception e) {
            log.error("Error starting session", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("HX-Redirect", "/retro/" + retroId + "?error=start_failed")
                .build();
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
            retroService.advanceToNextStep(retroId);
            
            // Publish step advanced event
            try {
                eventService.publish(RetroEvent.stepAdvanced(retroId));
            } catch (Exception eventError) {
                log.error("Failed to publish step advanced event: {}", eventError.getMessage());
                // Continue anyway - event publishing failure shouldn't block user flow
            }
            
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
    
    @PostMapping("/{retroId}/step/{stepId}/response")
    @PreAuthorize("hasAnyRole('USER', 'GUEST')")
    public ResponseEntity<Void> submitResponse(
            @PathVariable UUID retroId,
            @PathVariable Long stepId,
            @RequestParam Map<String, String> params,
            HttpServletRequest httpRequest) {

        log.debug("Submitting response for retro: {}, step: {}", retroId, stepId);

        try {
            RetroSession session = retroService.getSessionById(retroId);
            if (session == null) {
                return ResponseEntity.notFound().build();
            }

            Participant participant = participantService.getParticipantForSession(httpRequest, retroId);
            if (participant == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            RetroStep step = retroService.getCurrentStep(retroId);
            if (step == null || !step.getId().equals(stepId)) {
                return ResponseEntity.badRequest().build();
            }

            // Convert String params to Object map for JSON storage
            Map<String, Object> responseData = new HashMap<>(params);

            // Convert numeric strings to integers for RATING pattern
            if (step.getDataPattern() == DataPattern.RATING) {
                if (params.containsKey("rating")) {
                    responseData.put("rating", Integer.parseInt(params.get("rating")));
                }

                // Read scale configuration from step
                Map<String, Object> config = step.getConfig();
                Map<String, Object> scale = config != null ? (Map<String, Object>) config.get("scale") : null;

                if (scale != null) {
                    responseData.put("minRating", scale.get("min"));
                    responseData.put("maxRating", scale.get("max"));
                } else {
                    // Fallback defaults
                    responseData.put("minRating", 1);
                    responseData.put("maxRating", 10);
                }
            }

            responseService.submitResponse(session, stepId, participant, step.getDataPattern(), responseData);

            log.info("Submitted {} response for participant: {} in step: {}",
                step.getDataPattern(), participant.getDisplayName(), stepId);

            return ResponseEntity.ok()
                .header("HX-Trigger", "responseSubmitted")
                .build();

        } catch (Exception e) {
            log.error("Error submitting response: ", e);
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

}