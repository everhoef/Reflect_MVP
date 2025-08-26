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

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/retro")
@RequiredArgsConstructor
@Slf4j
public class RetroApiController {
    private final RetroSessionService retroService;
    private final ParticipantService participantService;
    private final EventService eventService;

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
        
        
        log.debug("Join request for retro: {} by user: {}", 
            request.retroId(), authentication.getName());
        
        if (!retroService.sessionExists(request.retroId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .header("HX-Redirect", "/home?error=session_not_found")
                .build();
        }
        
        // Get the session first to pass to addParticipantToSession
        RetroSession sessionToJoin = retroService.getSessionById(request.retroId());
        if (sessionToJoin == null) { // Should not happen if sessionExists passed
             return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .header("HX-Redirect", "/home?error=session_not_found")
                .build();
        }

        try {
            Participant participant = participantService.addParticipantToSession(httpRequest, sessionToJoin, ParticipantRole.PARTICIPANT);
            
            log.debug("Successfully added participant to session, redirecting to: /retro/{}", request.retroId());
            
            // Publish participant joined event
            try {
                eventService.publish(RetroEvent.participantJoined(request.retroId(), participant.getDisplayName()));
                log.info("Published PARTICIPANT_JOINED event for '{}'", participant.getDisplayName());
            } catch (Exception eventError) {
                log.error("Failed to publish PARTICIPANT_JOINED event: {}", eventError.getMessage());
                // Continue anyway - event publishing failure shouldn't block user flow
            }
            
            return ResponseEntity.status(HttpStatus.FOUND)
                .header("HX-Redirect", "/retro/" + request.retroId())
                .build();
            
        } catch (Exception e) {
            log.error("Error joining retro session {}: ", request.retroId(), e);
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

}