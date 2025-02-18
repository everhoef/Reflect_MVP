package direct.reflect.facilitator.controller;

import direct.reflect.facilitator.messaging.RetroEvent;
import direct.reflect.facilitator.service.EventService;
import direct.reflect.facilitator.service.ParticipantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.Mockito.when;

@WebFluxTest(RetroEventController.class)
@ContextConfiguration(classes = {TestConfig.class})
class RetroEventControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private EventService eventService;

    @Autowired
    private ParticipantService participantService;

    @Test
    @WithMockUser
    void shouldEstablishSSEConnectionForParticipant() {
        UUID retroId = UUID.randomUUID();
        RetroEvent<Void> event = RetroEvent.retroCreated(retroId, "User1");
        
        when(participantService.isParticipating(retroId)).thenReturn(true);
        when(eventService.subscribeToRetro(retroId))
            .thenReturn(Flux.just(event));

        Flux<ServerSentEvent<RetroEvent<?>>> response = webTestClient.get()
            .uri("/api/retro/events/{retroId}", retroId)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .returnResult(new ParameterizedTypeReference<ServerSentEvent<RetroEvent<?>>>() {})
            .getResponseBody();

        StepVerifier.create(response)
            .assertNext(sse -> {
                assert sse.data() != null;
                assert sse.data().equals(event);
                assert sse.event().equals("retro-event");
            })
            .thenCancel()
            .verify();
    }

    @Test
    @WithMockUser
    void shouldRejectNonParticipant() {
        UUID retroId = UUID.randomUUID();
        when(participantService.isParticipating(retroId)).thenReturn(false);

        webTestClient.get()
            .uri("/api/retro/events/{retroId}", retroId)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isForbidden();
    }

    @Test
    @WithMockUser
    void shouldReceiveKeepAliveMessages() {
        UUID retroId = UUID.randomUUID();
        when(participantService.isParticipating(retroId)).thenReturn(true);
        when(eventService.subscribeToRetro(retroId))
            .thenReturn(Flux.empty());

        Flux<ServerSentEvent<RetroEvent<?>>> response = webTestClient.get()
            .uri("/api/retro/events/{retroId}", retroId)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .returnResult(new ParameterizedTypeReference<ServerSentEvent<RetroEvent<?>>>() {})
            .getResponseBody();

        StepVerifier.create(response)
            .assertNext(sse -> {
                assert sse.comment() != null;
                assert sse.comment().equals("keepalive");
            })
            .thenCancel()
            .verify();
    }

    @Test
    @WithMockUser
    void shouldReceiveMultipleEvents() {
        UUID retroId = UUID.randomUUID();
        RetroEvent<String> event1 = RetroEvent.phaseStarted(retroId, "User1", "reflection");
        RetroEvent<String> event2 = RetroEvent.phaseStarted(retroId, "User1", "grouping");
        
        when(participantService.isParticipating(retroId)).thenReturn(true);
        when(eventService.subscribeToRetro(retroId))
            .thenReturn(Flux.just(event1, event2));

        Flux<ServerSentEvent<RetroEvent<?>>> response = webTestClient.get()
            .uri("/api/retro/events/{retroId}", retroId)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .returnResult(new ParameterizedTypeReference<ServerSentEvent<RetroEvent<?>>>() {})
            .getResponseBody();

        StepVerifier.create(response)
            .assertNext(sse -> {
                assert sse.data().equals(event1);
                assert sse.event().equals("retro-event");
            })
            .assertNext(sse -> {
                assert sse.data().equals(event2);
                assert sse.event().equals("retro-event");
            })
            .thenCancel()
            .verify();    }}
