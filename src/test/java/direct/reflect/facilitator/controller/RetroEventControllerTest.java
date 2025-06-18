package direct.reflect.facilitator.controller;

import direct.reflect.facilitator.messaging.RetroEvent;
import direct.reflect.facilitator.service.EventService;
import direct.reflect.facilitator.service.ParticipantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = RetroEventController.class)
@AutoConfigureWebTestClient(timeout = "PT35S")
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

        when(participantService.isParticipating(any(ServerWebExchange.class), eq(retroId))).thenReturn(Mono.just(true));
        when(participantService.updateLastSeen(any(ServerWebExchange.class), eq(retroId))).thenReturn(Mono.empty());
        when(eventService.subscribeToRetro(eq(retroId))).thenReturn(Flux.<RetroEvent<?>>just(event).delayElements(Duration.ofMillis(10)));

        Flux<ServerSentEvent<RetroEvent<?>>> response = webTestClient.get()
            .uri("/api/retro/{retroId}/events", retroId)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .returnResult(new ParameterizedTypeReference<ServerSentEvent<RetroEvent<?>>>() {})
            .getResponseBody();

        StepVerifier.create(response)
            .assertNext(sse -> {
                assertNotNull(sse.data(), "SSE data should not be null");
                assertEquals(retroId, sse.data().retroId(), "Retro ID should match");
                assertNotNull(sse.event(), "SSE event should not be null");
                assertEquals("retro-created", sse.event(), "Event type should be retro-created");
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

        when(participantService.isParticipating(any(ServerWebExchange.class), eq(retroId))).thenReturn(Mono.just(true));
        when(participantService.updateLastSeen(any(ServerWebExchange.class), eq(retroId))).thenReturn(Mono.empty());
        // Return an empty flux for actual events, so we only test keep-alive
        when(eventService.subscribeToRetro(eq(retroId))).thenReturn(Flux.empty()); 

        Flux<ServerSentEvent<Object>> response = webTestClient.get()
            .uri("/api/retro/{retroId}/events", retroId)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .returnResult(new ParameterizedTypeReference<ServerSentEvent<Object>>() {})
            .getResponseBody();

        StepVerifier.create(response.take(1)) // Take 1 keep-alive event
            .expectNextMatches(sse -> {
                // SSE Spec: lines that begin with a colon are comments and are ignored.
                // Our keep-alive is sent as a comment.
                assertNotNull(sse.comment(), "Keep-alive comment should not be null");
                assertEquals("keepalive", sse.comment(), "Comment should be 'keepalive'");
                return true;
            })
            .thenCancel()
            .verify(Duration.ofSeconds(20)); // Allow time for keep-alive to be sent
    }

    @Test
    @WithMockUser
    void shouldReceiveMultipleEvents() {
        UUID retroId = UUID.randomUUID();
        RetroEvent<String> event1 = new RetroEvent<>(retroId, RetroEvent.EventType.PHASE_STARTED, "User1", Instant.now(), "Reflection");
        RetroEvent<Integer> event2 = new RetroEvent<>(retroId, RetroEvent.EventType.STEP_ADVANCED, "User2", Instant.now(), 2);

        when(participantService.isParticipating(any(ServerWebExchange.class), eq(retroId))).thenReturn(Mono.just(true));
        when(participantService.updateLastSeen(any(ServerWebExchange.class), eq(retroId))).thenReturn(Mono.empty());
        when(eventService.subscribeToRetro(eq(retroId))).thenReturn(Flux.<RetroEvent<?>>just(event1, event2));

        Flux<ServerSentEvent<RetroEvent<?>>> response = webTestClient.get()
            .uri("/api/retro/{retroId}/events", retroId)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .returnResult(new ParameterizedTypeReference<ServerSentEvent<RetroEvent<?>>>() {})
            .getResponseBody();

        StepVerifier.create(response)
            .assertNext(sse -> {
                assertNotNull(sse.data(), "SSE data for event1 should not be null");
                assertEquals(event1, sse.data(), "SSE data should match event1");
                assertNotNull(sse.event(), "SSE event for event1 should not be null");
                assertEquals("phase-started", sse.event(), "Event type should be phase-started");
            })
            .assertNext(sse -> {
                assertNotNull(sse.data(), "SSE data for event2 should not be null");
                assertEquals(event2, sse.data(), "SSE data should match event2");
                assertNotNull(sse.event(), "SSE event for event2 should not be null");
                assertEquals("step-advanced", sse.event(), "Event type should be step-advanced");
            })
            .thenCancel()
            .verify();
    }
}
