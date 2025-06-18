package direct.reflect.facilitator.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ServerWebExchange;

import direct.reflect.facilitator.domain.entity.RetroSession;
import direct.reflect.facilitator.domain.entity.Participant;
import direct.reflect.facilitator.domain.enums.ParticipantRole;
import direct.reflect.facilitator.service.ParticipantService;
import direct.reflect.facilitator.service.RetroSessionService;
import direct.reflect.facilitator.service.EventService;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;

import reactor.core.publisher.Mono;

@WebFluxTest(RetroApiController.class)
public class RetroApiControllerTest {

    @Autowired
    private WebTestClient webTestClient;
    
    @MockitoBean 
    private RetroSessionService retroSessionService;
    
    @MockitoBean 
    private ParticipantService participantService;
    
    @MockitoBean 
    private EventService eventService;
    
    @Test
    @WithMockUser
    void shouldCreateRetrospectiveAndRedirect() {
        // Arrange
        RetroSession mockSession = new RetroSession();
        UUID retroId = UUID.randomUUID();
        mockSession.setId(retroId); 
        mockSession.setName("Test Session");
        
        Participant mockParticipant = new Participant();
        mockParticipant.setDisplayName("TestUser");
        mockParticipant.setParticipantId(UUID.randomUUID()); 
        mockParticipant.setSession(mockSession); 
        
        // Corrected argument order for createAndAssignFacilitatorForSession
        when(participantService.createAndAssignFacilitatorForSession(eq("Test Session"), eq("TestUser"), any(ServerWebExchange.class)))
            .thenReturn(Mono.just(mockParticipant));

        RetroApiController.CreateRetroRequest request = new RetroApiController.CreateRetroRequest("Test Session", "TestUser");

        // Act & Assert
        webTestClient
            .mutateWith(csrf()) // Add CSRF token
            .post().uri("/api/retro/create")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isFound() // Expect 302 Found for redirect
            .expectHeader().valueEquals("HX-Redirect", "/retro/" + retroId);
    }
    
    @Test
    @WithMockUser
    void shouldJoinRetrospectiveAndRedirect() {
        // Arrange
        UUID retroId = UUID.randomUUID();
        String displayName = "Joiner";
        RetroApiController.JoinRetroRequest request = new RetroApiController.JoinRetroRequest(retroId, displayName);

        RetroSession mockSession = new RetroSession();
        mockSession.setId(retroId);
        mockSession.setName("Existing Session");

        Participant mockParticipant = new Participant();
        mockParticipant.setParticipantId(UUID.randomUUID()); 
        mockParticipant.setDisplayName(displayName);
        mockParticipant.setSession(mockSession);

        when(retroSessionService.sessionExists(retroId)).thenReturn(true);
        when(retroSessionService.getSessionById(retroId)).thenReturn(mockSession); 
        when(participantService.addParticipantToSession(any(ServerWebExchange.class), eq(mockSession), eq(displayName), eq(ParticipantRole.PARTICIPANT)))
            .thenReturn(Mono.just(mockParticipant));

        // Act & Assert
        webTestClient
            .mutateWith(csrf()) // Add CSRF token
            .post().uri("/api/retro/join")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isFound()
            .expectHeader().valueEquals("HX-Redirect", "/retro/" + retroId);
    }
}
