package direct.reflect.facilitator.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import direct.reflect.facilitator.messaging.RetroEvent;
import direct.reflect.facilitator.service.EventService;
import direct.reflect.facilitator.service.ParticipantService;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.UUID;

import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;


@RestController
@RequestMapping("api/retro")
@RequiredArgsConstructor
public class RetroEventController {
    private final EventService eventService;
    private final ParticipantService participantService;
    
    @GetMapping("/events/{retroId}")
    public Flux<ServerSentEvent<RetroEvent<?>>> getRetroEvents(@PathVariable UUID retroId) {
        if (!participantService.isParticipating(retroId)) {
            return Flux.error(new AccessDeniedException("Not a participant"));
        }

        return eventService.subscribeToRetro(retroId)
            .map(event -> ServerSentEvent.<RetroEvent<?>>builder()
                .id(UUID.randomUUID().toString())
                .event("retro-event")
                .data(event)
                .build())
            .mergeWith(Flux.interval(Duration.ofSeconds(30))
                .map(i -> ServerSentEvent.<RetroEvent<?>>builder()
                    .comment("keepalive")
                    .build()));
    }
}