package direct.reflect.facilitator.controller;

import direct.reflect.facilitator.messaging.RetroEvent;
import direct.reflect.facilitator.service.EventService;
import direct.reflect.facilitator.service.ParticipantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = RetroEventController.class)
@EnableAutoConfiguration(exclude = {
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class
})
class RetroEventControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private EventService eventService;

    @MockitoBean
    private ParticipantService participantService;

    @Test
    @WithMockUser
    void shouldEstablishSSEConnectionForParticipant() {
        UUID retroId = UUID.randomUUID();
        RetroEvent<Void> event = new RetroEvent<>(retroId, RetroEvent.EventType.RETRO_CREATED, "User1", Instant.now(), null);
        
        when(participantService.isParticipating(any(ServerWebExchange.class), eq(retroId)))
            .thenReturn(Mono.just(true));
        when(participantService.updateLastSeen(any(ServerWebExchange.class)))
            .thenReturn(Mono.empty());
        when(eventService.subscribeToRetro(retroId))
            .thenReturn(Flux.just(event));

        Flux<ServerSentEvent<RetroEvent<?>>> response = webTestClient.get()
            .uri("/api/retro/{retroId}/events", retroId)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .returnResult(new ParameterizedTypeReference<ServerSentEvent<RetroEvent<?>>>() {})
            .getResponseBody();

        StepVerifier.create(response)
            .assertNext(sse -> {
                assert sse.data() != null;
                assert sse.data().retroId().equals(retroId);
                assert sse.event().equals("retro-created");
            })
            .thenCancel()
            .verify();
    }

    @Test
    @WithMockUser
    void shouldRejectNonParticipant() {
        UUID retroId = UUID.randomUUID();
        
        when(participantService.isParticipating(any(ServerWebExchange.class), eq(retroId)))
            .thenReturn(Mono.just(false));

        webTestClient.get()
            .uri("/api/retro/{retroId}/events", retroId)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isForbidden();
    }

    @Test
    @WithMockUser
    void shouldHandleKeepAliveMessages() {
        UUID retroId = UUID.randomUUID();
        
        // Mock participant service
        when(participantService.isParticipating(any(ServerWebExchange.class), eq(retroId)))
            .thenReturn(Mono.just(true));
        when(participantService.updateLastSeen(any(ServerWebExchange.class)))
            .thenReturn(Mono.empty());
        
        // Create a flux with a single event
        RetroEvent<Void> event = new RetroEvent<>(retroId, RetroEvent.EventType.RETRO_CREATED, "User1", Instant.now(), null);
        when(eventService.subscribeToRetro(retroId))
            .thenReturn(Flux.just(event));

        // Just test the response completes properly without waiting for actual keepalives
        webTestClient.mutate()
            .responseTimeout(Duration.ofMillis(500))
            .build()
            .get()
            .uri("/api/retro/{retroId}/events", retroId)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    @Test
    @WithMockUser
    void shouldReceiveMultipleEvents() {
        UUID retroId = UUID.randomUUID();
        RetroEvent<String> event1 = new RetroEvent<>(retroId, 
            RetroEvent.EventType.PHASE_STARTED, "User1", Instant.now(), "reflection");
        RetroEvent<String> event2 = new RetroEvent<>(retroId,
            RetroEvent.EventType.PHASE_STARTED, "User1", Instant.now(), "grouping");
        
        when(participantService.isParticipating(any(ServerWebExchange.class), eq(retroId)))
            .thenReturn(Mono.just(true));
        when(participantService.updateLastSeen(any(ServerWebExchange.class)))
            .thenReturn(Mono.empty());
        when(eventService.subscribeToRetro(retroId))
            .thenReturn(Flux.just(event1, event2));

        Flux<ServerSentEvent<RetroEvent<?>>> response = webTestClient.get()
            .uri("/api/retro/{retroId}/events", retroId)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .returnResult(new ParameterizedTypeReference<ServerSentEvent<RetroEvent<?>>>() {})
            .getResponseBody();

        StepVerifier.create(response)
            .assertNext(sse -> {
                assert sse.data().equals(event1);
                assert sse.event().equals("phase-started");
            })
            .assertNext(sse -> {
                assert sse.data().equals(event2);
                assert sse.event().equals("phase-started");
            })
            .thenCancel()
            .verify();
    }
}
