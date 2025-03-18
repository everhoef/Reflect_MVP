package direct.reflect.facilitator.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import direct.reflect.facilitator.messaging.RetroEvent;
import direct.reflect.facilitator.service.EventService;
import direct.reflect.facilitator.service.ParticipantService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

@RestController
@RequestMapping("/api/retro")
@RequiredArgsConstructor
@Slf4j
public class RetroEventController {
    private final EventService eventService;
    private final ParticipantService participantService;
    
    @GetMapping(value = "/{retroId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<RetroEvent<?>>> getRetroEvents(
            @PathVariable UUID retroId,
            ServerWebExchange exchange) {
        
        log.info("SSE connection requested for retro {}", retroId);
        
        // Check if user is a participant in this retro session
        return participantService.isParticipating(exchange, retroId)
            .flatMap(isParticipating -> {
                if (!isParticipating) {
                    log.warn("Unauthorized SSE connection attempt for retro {}", retroId);
                    return Flux.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a participant"))
                        .next();  // Convert to Mono for flatMap
                }
                
                // Subscribe to events stream
                return participantService.updateLastSeen(exchange)
                    .thenReturn(true);
            })
            .flatMapMany(isParticipating -> subscribeToEvents(retroId, exchange));
    }
    
    private Flux<ServerSentEvent<RetroEvent<?>>> subscribeToEvents(
            UUID retroId, ServerWebExchange exchange) {
        
        log.info("Starting SSE stream for retroId: {}", retroId);
        
        // Create a flux of events with keepalive
        return eventService.subscribeToRetro(retroId)
            .map(event -> {
                log.debug("Sending event: {} for retro: {}", event.type(), retroId);
                String eventName = toKebabCase(event.type().toString());
                return ServerSentEvent.<RetroEvent<?>>builder()
                    .id(UUID.randomUUID().toString())
                    .event(eventName)
                    .data(event)
                    .build();
            })
            // Add keepalive events
            .mergeWith(Flux.interval(Duration.ofSeconds(30))
                .flatMap(i -> 
                    // Update last seen on every keepalive
                    participantService.updateLastSeen(exchange)
                        .thenReturn(ServerSentEvent.<RetroEvent<?>>builder()
                            .comment("keepalive")
                            .build())
                ));
    }

    // Helper method to convert event types to kebab-case
    private String toKebabCase(String input) {
        if (input == null) return "";
        return input.replaceAll("([a-z])([A-Z])", "$1-$2")
               .replaceAll("_", "-")
               .toLowerCase();
    }
}