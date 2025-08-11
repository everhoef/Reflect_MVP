package direct.reflect.facilitator.facilitation;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import direct.reflect.facilitator.eventing.EventService;
import direct.reflect.facilitator.eventing.RetroEvent;
import direct.reflect.facilitator.common.RequestValidationService;
import direct.reflect.facilitator.common.ResponseService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@RestController
@RequestMapping("/api/retro")
@RequiredArgsConstructor
@Slf4j
public class RetroApiController {
    private final RetroSessionService retroService;
    private final ParticipantService participantService;
    private final EventService eventService;
    private final RequestValidationService validationService;
    private final ResponseService responseService;

    @PostMapping(value = "/create", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('USER', 'GUEST')")
    public ResponseEntity<Void> createRetrospective(
            @RequestBody CreateRetroRequest request,
            HttpServletRequest httpRequest,
            Authentication authentication) {
         
        log.debug("=== CREATE REQUEST START ===");
        log.debug("Request body: {}", request);
        log.debug("Authentication: {} (type: {})", authentication.getName(), authentication.getClass().getSimpleName());
        log.debug("Request method: {}", httpRequest.getMethod());
        log.debug("Request URI: {}", httpRequest.getRequestURI());
        
        log.debug("Creating new retro session with name: {} by user: {}", 
            request.sessionName(), authentication.getName());
        
        var validation = validationService.validateSessionName(request.sessionName());
        if (!validation.isValid()) {
            log.debug("Session name validation failed: {}", validation.getErrorMessage());
            return responseService.createBadRequestResponse("/home", validation.getErrorCode());
        }
        
        try {
            // Display name is derived from the authentication context (no need to pass it)
            Participant participant = participantService.createAndAssignFacilitatorForSession(request.sessionName(), null, httpRequest);
            
            // The participant object now holds the session reference
            RetroSession retro = participant.getSession();
            log.info("Created new retro session: {} (id: {}) by facilitator: {}", 
                retro.getName(), retro.getId(), participant.getDisplayName());
            
            // Optionally publish an event that a new session was created
            // eventService.publish(RetroEvent.sessionCreated(retro.getId(), retro.getName()));
            return responseService.createRedirectResponse("/retro/" + retro.getId());
            
        } catch (Exception e) {
            log.error("Error creating retro session: ", e);
            String errorCode = responseService.mapSessionCreationException(e);
            return responseService.createErrorRedirectResponse("/home", errorCode);
        }
    }

    @PostMapping(value = "/join", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('USER', 'GUEST')")
    public ResponseEntity<Void> joinRetrospective(
            @RequestBody JoinRetroRequest request,
            HttpServletRequest httpRequest,
            Authentication authentication) {
        
        log.debug("=== JOIN REQUEST START ===");
        log.debug("Request retroId: {}", request.retroId());
        log.debug("Authentication: {} (type: {})", authentication.getName(), authentication.getClass().getSimpleName());
        log.debug("Request path: {}", httpRequest.getRequestURI());
        
        var validation = validationService.validateRetroId(request.retroId());
        if (!validation.isValid()) {
            log.debug("Retro ID validation failed: {}", validation.getErrorMessage());
            return responseService.createBadRequestResponse("/home", validation.getErrorCode());
        }
        
        log.debug("Join request for retro: {} by user: {}", 
            request.retroId(), authentication.getName());
        
        if (!retroService.sessionExists(request.retroId())) {
            return responseService.createNotFoundResponse("/home", "session_not_found");
        }
        
        // Get the session first to pass to addParticipantToSession
        RetroSession sessionToJoin = retroService.getSessionById(request.retroId());
        if (sessionToJoin == null) { // Should not happen if sessionExists passed
             return responseService.createNotFoundResponse("/home", "session_not_found_critical");
        }

        try {
            Participant participant = participantService.addParticipantToSession(httpRequest, sessionToJoin, null, ParticipantRole.PARTICIPANT);
            
            log.debug("Successfully added participant to session, redirecting to: /retro/{}", request.retroId());
            
            // Publish participant joined event
            try {
                eventService.publish(RetroEvent.participantJoined(request.retroId(), participant.getDisplayName()));
                log.info("Published PARTICIPANT_JOINED event for '{}'", participant.getDisplayName());
            } catch (Exception eventError) {
                log.error("Failed to publish PARTICIPANT_JOINED event: {}", eventError.getMessage());
                // Continue anyway - event publishing failure shouldn't block user flow
            }
            
            return responseService.createRedirectResponse("/retro/" + request.retroId());
            
        } catch (Exception e) {
            log.error("Error joining retro session {}: ", request.retroId(), e);
            String errorCode = responseService.mapSessionJoinException(e);
            return responseService.createErrorRedirectResponse("/home", errorCode);
        }
    }

    @PostMapping("/{retroId}/start")
    @PreAuthorize("hasAnyRole('USER', 'GUEST')") // Both users and guests can start sessions if they're facilitators
    public ResponseEntity<Void> startSession(
            @PathVariable UUID retroId,
            HttpServletRequest httpRequest) {
        
        boolean isFacilitator = participantService.isFacilitator(httpRequest, retroId);
        if (!isFacilitator) {
            return responseService.createForbiddenResponse("/retro/" + retroId);
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
            
            return responseService.createRedirectResponse("/retro/" + retroId);
        } catch (Exception e) {
            log.error("Error starting session", e);
            String errorCode = responseService.mapSessionOperationException(e, "start");
            return responseService.createServerErrorResponse("/retro/" + retroId, errorCode);
        }
    }

    @PostMapping("/{retroId}/next")
    @PreAuthorize("hasAnyRole('USER', 'GUEST')") // Both users and guests can advance sessions if they're facilitators
    public ResponseEntity<Void> nextStep(
            @PathVariable UUID retroId,
            HttpServletRequest httpRequest) {
        
        boolean isFacilitator = participantService.isFacilitator(httpRequest, retroId);
        if (!isFacilitator) {
            return responseService.createForbiddenResponse("/retro/" + retroId);
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
            
            return responseService.createSuccessResponseWithHeader("HX-Trigger", "stepAdvanced");
        } catch (Exception e) {
            log.error("Error advancing step", e);
            String errorCode = responseService.mapSessionOperationException(e, "next_step");
            return responseService.createServerErrorResponse("/retro/" + retroId, errorCode);
        }
    }
    
    // Simple records for JSON requests
    public record CreateRetroRequest(String sessionName) {}
    public record JoinRetroRequest(UUID retroId) {}
}