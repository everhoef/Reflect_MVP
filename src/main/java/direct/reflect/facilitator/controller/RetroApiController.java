package direct.reflect.facilitator.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;

import direct.reflect.facilitator.domain.entity.RetroSession;
import direct.reflect.facilitator.domain.entity.Participant;
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
        
        // Only anonymous users need to provide displayName
        String displayName = request.displayName();
        
        try {
            RetroSession retro = retroService.createNewSession(request.sessionName());
            
            return participantService.addFacilitator(exchange, retro.getRetroId(), displayName)
                .map(participant -> {
                    log.info("Created new retro session: {} (id: {}) by user: {}", 
                        retro.getName(), retro.getRetroId(), participant.getDisplayName());
                    
                    return ResponseEntity.ok()
                        .header("HX-Redirect", "/retro/" + retro.getRetroId())
                        .build();
                });
                
        } catch (Exception e) {
            log.error("Failed to create retro session", e);
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("HX-Redirect", "/?error=creation_failed")
                .build());
        }
    }

    @PostMapping(value = "/join", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Void>> joinRetrospective(
            @RequestBody JoinRetroRequest request,
            ServerWebExchange exchange) {
        
        if (request.retroId() == null) {
            return Mono.just(ResponseEntity.badRequest()
                .header("HX-Redirect", "/?error=missing_id")
                .build());
        }
        
        // Only anonymous users need to provide displayName
        String displayName = request.displayName();
        
        log.debug("Join request for retro: {} with display name: {}", request.retroId(), displayName);
        
        if (!retroService.sessionExists(request.retroId())) {
            log.warn("Attempted to join non-existent retro: {}", request.retroId());
            return Mono.just(ResponseEntity.notFound()
                .header("HX-Redirect", "/?error=session_not_found")
                .build());
        }
        
        try {
            return participantService.addParticipant(exchange, request.retroId(), displayName)
                .map(participant -> {
                    log.info("User {} joined retro session: {}", 
                        participant.getDisplayName(), request.retroId());
                    
                    // Publish participant joined event
                    eventService.publish(RetroEvent.participantJoined(
                        request.retroId(), 
                        participant.getDisplayName()));
                        
                    return ResponseEntity.ok()
                        .header("HX-Redirect", "/retro/" + request.retroId())
                        .build();
                });
        } catch (Exception e) {
            log.error("Failed to join retro session", e);
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("HX-Redirect", "/?error=join_failed")
                .build());
        }
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