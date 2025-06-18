package direct.reflect.facilitator.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;

import direct.reflect.facilitator.domain.entity.RetroSession;
import direct.reflect.facilitator.domain.enums.ParticipantRole;
import direct.reflect.facilitator.service.ParticipantService;
import direct.reflect.facilitator.service.RetroSessionService;
import direct.reflect.facilitator.service.EventService;
import direct.reflect.facilitator.messaging.RetroEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/retro")
@RequiredArgsConstructor
@Slf4j
public class RetroApiController {
    private final RetroSessionService retroService;
    private final ParticipantService participantService;
    private final EventService eventService;

    @PostMapping(value = "/create", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Void>> createRetrospective(
            @RequestBody CreateRetroRequest request,
            ServerWebExchange exchange) {
        
        log.debug("Creating new retro session with name: {}", request.sessionName());
        
        if (request.sessionName() == null || request.sessionName().isBlank()) {
            return Mono.just(ResponseEntity.badRequest()
                .header("HX-Redirect", "/?error=missing_name")
                .build());
        }
        
        // Display name is passed to participant service to handle
        String displayName = request.displayName(); 
        
        return participantService.createAndAssignFacilitatorForSession(request.sessionName(), displayName, exchange)
            .map(participant -> {
                // The participant object now holds the session reference
                RetroSession retro = participant.getSession();
                log.info("Created new retro session: {} (id: {}) by facilitator: {}", 
                    retro.getName(), retro.getId(), participant.getDisplayName());
                
                // Optionally publish an event that a new session was created
                // eventService.publish(RetroEvent.sessionCreated(retro.getId(), retro.getName()));
                return ResponseEntity.status(HttpStatus.FOUND)
                    .header("HX-Redirect", "/retro/" + retro.getId())
                    .<Void>build(); // Ensure this returns ResponseEntity<Void>
            })
            .onErrorResume(e -> {
                log.error("Error creating retro session: ", e);
                String errorRedirect = "/?error=creation_failed";
                if (e instanceof IllegalStateException) {
                    errorRedirect = "/?error=active_session_exists"; // More specific error
                }
                return Mono.just(ResponseEntity.status(HttpStatus.SEE_OTHER)
                    .header("HX-Redirect", errorRedirect)
                    .<Void>build());
            });
    }

    @PostMapping(value = "/join", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Void>> joinRetrospective(
            @RequestBody JoinRetroRequest request,
            ServerWebExchange exchange) {
        
        if (request.retroId() == null) {
            return Mono.just(ResponseEntity.badRequest()
                .header("HX-Redirect", "/?error=missing_retro_id")
                .build());
        }
        
        // Display name is passed to participant service to handle
        String displayName = request.displayName(); 
        
        log.debug("Join request for retro: {} with display name: {}", request.retroId(), displayName);
        
        if (!retroService.sessionExists(request.retroId())) {
            return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                .header("HX-Redirect", "/?error=session_not_found")
                .build());
        }
        
        // Get the session first to pass to addParticipantToSession
        RetroSession sessionToJoin = retroService.getSessionById(request.retroId());
        if (sessionToJoin == null) { // Should not happen if sessionExists passed
             return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                .header("HX-Redirect", "/?error=session_not_found_critical")
                .build());
        }

        return participantService.addParticipantToSession(exchange, sessionToJoin, displayName, ParticipantRole.PARTICIPANT)
            .map(participant -> ResponseEntity.status(HttpStatus.FOUND)
                .header("HX-Redirect", "/retro/" + request.retroId())
                .<Void>build())
            .onErrorResume(IllegalStateException.class, e -> Mono.just(ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header("HX-Redirect", "/?error=active_session_exists")
                .<Void>build()))
            .onErrorResume(e -> {
                log.error("Error joining retro session {}: ", request.retroId(), e);
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("HX-Redirect", "/?error=join_failed")
                    .<Void>build());
            });
    }

    @PostMapping("/{retroId}/start")
    public Mono<ResponseEntity<Void>> startSession(
            @PathVariable UUID retroId,
            ServerWebExchange exchange) {
        
        return participantService.isFacilitator(exchange, retroId)
            .flatMap(isFacilitator -> {
                if (!isFacilitator) {
                    return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .header("HX-Redirect", "/retro/" + retroId)
                        .build());
                }
                
                try {
                    retroService.startSession(retroId);
                    
                    // Publish session started event
                    eventService.publish(RetroEvent.sessionStarted(retroId));
                    
                    return Mono.just(ResponseEntity.ok()
                        .header("HX-Redirect", "/retro/" + retroId)
                        .build());
                } catch (Exception e) {
                    log.error("Error starting session", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .header("HX-Redirect", "/retro/" + retroId + "?error=start_failed")
                        .build());
                }
            });
    }

    @PostMapping("/{retroId}/next")
    public Mono<ResponseEntity<Void>> nextStep(
            @PathVariable UUID retroId,
            ServerWebExchange exchange) {
        
        return participantService.isFacilitator(exchange, retroId)
            .flatMap(isFacilitator -> {
                if (!isFacilitator) {
                    return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .header("HX-Redirect", "/retro/" + retroId)
                        .build());
                }
                
                try {
                    retroService.advanceToNextStep(retroId);
                    
                    // Publish step advanced event
                    eventService.publish(RetroEvent.stepAdvanced(retroId));
                    
                    return Mono.just(ResponseEntity.ok()
                        .header("HX-Trigger", "stepAdvanced")
                        .build());
                } catch (Exception e) {
                    log.error("Error advancing step", e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .header("HX-Redirect", "/retro/" + retroId + "?error=next_step_failed")
                        .build());
                }
            });
    }
    
    // Simple records for JSON requests
    public record CreateRetroRequest(String sessionName, String displayName) {}
    public record JoinRetroRequest(UUID retroId, String displayName) {}
}