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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.UUID;

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
        
        // Create a flux with a single event to avoid waiting for keep-alive
        RetroEvent<Void> event = RetroEvent.retroCreated(retroId, "User1");
        when(eventService.subscribeToRetro(retroId))
            .thenReturn(Flux.just(event));

        webTestClient.mutate()
            .responseTimeout(Duration.ofSeconds(1))
            .build()
            .get()
            .uri("/api/retro/events/{retroId}", retroId)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.TEXT_EVENT_STREAM_VALUE);
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
            .verify();
    }
}
