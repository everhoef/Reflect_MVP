package direct.reflect.facilitator.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ServerWebExchange;

import direct.reflect.facilitator.domain.entity.RetroSession;
import direct.reflect.facilitator.domain.entity.Participant;
import direct.reflect.facilitator.service.ParticipantService;
import direct.reflect.facilitator.service.RetroSessionService;
import direct.reflect.facilitator.service.EventService;
import direct.reflect.facilitator.messaging.RetroEvent;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import reactor.core.publisher.Mono;

@WebFluxTest(RetroApiController.class)
public class RetroApiControllerTest {

    @Autowired
    private WebTestClient webTestClient;
    
    @MockitoBean // Updated from @MockBean
    private RetroSessionService retroSessionService;
    
    @MockitoBean // Updated from @MockBean
    private ParticipantService participantService;
    
    @MockitoBean // Updated from @MockBean
    private EventService eventService;
    
    @Test
    @WithMockUser
    void shouldCreateRetrospectiveAndRedirect() {
        // Arrange
        RetroSession mockSession = new RetroSession();
        UUID retroId = UUID.randomUUID();
        mockSession.setRetroId(retroId);
        mockSession.setName("Test Session");
        
        Participant mockParticipant = new Participant();
        mockParticipant.setDisplayName("TestUser");
        mockParticipant.setParticipantId("123");
        
        when(retroSessionService.createNewSession(anyString())).thenReturn(mockSession);
        when(participantService.addFacilitator(any(ServerWebExchange.class), eq(retroId), anyString()))
            .thenReturn(Mono.just(mockParticipant));
        
        // Act & Assert
        webTestClient.post()
            .uri("/api/retro/create")
            .bodyValue(new RetroApiController.CreateRetroRequest("Test Session", "TestUser"))
            .exchange()
            .expectStatus().isOk()
            .expectHeader().valueEquals("HX-Redirect", "/retro/" + retroId);
    }
    
    @Test
    @WithMockUser
    void shouldJoinRetrospectiveAndRedirect() {
        // Arrange
        UUID retroId = UUID.randomUUID();
        
        Participant mockParticipant = new Participant();
        mockParticipant.setDisplayName("TestUser");
        mockParticipant.setParticipantId("123");
        
        when(retroSessionService.sessionExists(retroId)).thenReturn(true);
        when(participantService.addParticipant(any(ServerWebExchange.class), eq(retroId), anyString()))
            .thenReturn(Mono.just(mockParticipant));
        when(eventService.publish(any(RetroEvent.class))).thenReturn(Mono.just(1L));
        
        // Act & Assert
        webTestClient.post()
            .uri("/api/retro/join")
            .bodyValue(new RetroApiController.JoinRetroRequest(retroId, "TestUser"))
            .exchange()
            .expectStatus().isOk()
            .expectHeader().valueEquals("HX-Redirect", "/retro/" + retroId);
    }
}
