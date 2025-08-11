package direct.reflect.facilitator.facilitation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import jakarta.servlet.http.HttpServletRequest;

import direct.reflect.facilitator.common.config.SecurityConfig;
import direct.reflect.facilitator.facilitation.RetroSession;
import direct.reflect.facilitator.facilitation.Participant;
import direct.reflect.facilitator.facilitation.ParticipantRole;
import direct.reflect.facilitator.facilitation.ParticipantService;
import direct.reflect.facilitator.facilitation.RetroSessionService;
import direct.reflect.facilitator.eventing.EventService;
import direct.reflect.facilitator.facilitation.RetroApiController;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

@WebMvcTest(controllers = RetroApiController.class)
@Import({SecurityConfig.class, direct.reflect.facilitator.common.exception.ApiExceptionHandler.class})
@EnableAutoConfiguration(exclude = {
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class
})
public class RetroApiControllerTest {

    @Autowired
    private MockMvc mockMvc;
    
    @MockitoBean 
    private RetroSessionService retroSessionService;
    
    @MockitoBean 
    private ParticipantService participantService;
    
    @MockitoBean 
    private EventService eventService;
    
    @MockitoBean
    private direct.reflect.facilitator.common.RequestValidationService validationService;
    
    @MockitoBean
    private direct.reflect.facilitator.common.ResponseService responseService;
    
    @Test
    @WithMockUser(roles = "USER")
    void shouldCreateRetrospectiveAndRedirect() throws Exception {
        // Arrange
        UUID retroId = UUID.randomUUID();
        RetroSession mockSession = new RetroSession();
        mockSession.setId(retroId); 
        mockSession.setName("Test Session");
        
        Participant mockParticipant = new Participant();
        mockParticipant.setParticipantId(UUID.randomUUID());
        mockParticipant.setDisplayName("Test User");
        mockParticipant.setSession(mockSession);

        RetroApiController.CreateRetroRequest request = new RetroApiController.CreateRetroRequest("Test Session");

        // Mock validation service
        when(validationService.validateSessionName("Test Session"))
            .thenReturn(direct.reflect.facilitator.common.RequestValidationService.ValidationResult.valid());
        
        // Mock response service
        when(responseService.createRedirectResponse("/retro/" + retroId))
            .thenReturn(org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.FOUND)
                .header("HX-Redirect", "/retro/" + retroId).build());

        when(participantService.createAndAssignFacilitatorForSession(eq("Test Session"), eq(null), any(HttpServletRequest.class)))
            .thenReturn(mockParticipant);
        
        // Act & Assert
        mockMvc.perform(post("/api/retro/create")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionName\":\"Test Session\"}"))
                .andDo(print())
                .andExpect(status().is3xxRedirection());
    }
    
    @Test
    @WithMockUser(roles = "GUEST")
    void shouldJoinRetrospectiveAndRedirect() throws Exception {
        // Arrange
        UUID retroId = UUID.randomUUID();
        RetroApiController.JoinRetroRequest request = new RetroApiController.JoinRetroRequest(retroId);

        RetroSession mockSession = new RetroSession();
        mockSession.setId(retroId);
        mockSession.setName("Existing Session");

        Participant mockParticipant = new Participant();
        mockParticipant.setParticipantId(UUID.randomUUID()); 
        mockParticipant.setDisplayName("Guest");
        mockParticipant.setSession(mockSession);

        // Mock validation service
        when(validationService.validateRetroId(retroId))
            .thenReturn(direct.reflect.facilitator.common.RequestValidationService.ValidationResult.valid());
        
        // Mock response service
        when(responseService.createRedirectResponse("/retro/" + retroId))
            .thenReturn(org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.FOUND)
                .header("HX-Redirect", "/retro/" + retroId).build());

        when(retroSessionService.sessionExists(retroId)).thenReturn(true);
        when(retroSessionService.getSessionById(retroId)).thenReturn(mockSession); 
        when(participantService.addParticipantToSession(any(HttpServletRequest.class), eq(mockSession), eq(null), eq(ParticipantRole.PARTICIPANT)))
            .thenReturn(mockParticipant);
        when(eventService.publish(any())).thenReturn("1234567890-0");

        // Act & Assert
        mockMvc.perform(post("/api/retro/join")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"retroId\":\"" + retroId + "\"}"))
                .andExpect(status().is3xxRedirection());
    }
    
    @Test
    @WithMockUser(roles = "GUEST")
    void shouldAllowGuestUserCreatingSession() throws Exception {
        // Arrange
        RetroApiController.CreateRetroRequest request = new RetroApiController.CreateRetroRequest("Test Session");
        
        UUID retroId = UUID.randomUUID();
        RetroSession mockSession = new RetroSession();
        mockSession.setId(retroId);
        mockSession.setName("Test Session");
        
        Participant mockParticipant = new Participant();
        mockParticipant.setParticipantId(UUID.randomUUID());
        mockParticipant.setDisplayName("Test Guest");
        mockParticipant.setSession(mockSession);

        // Mock validation service
        when(validationService.validateSessionName("Test Session"))
            .thenReturn(direct.reflect.facilitator.common.RequestValidationService.ValidationResult.valid());
        
        // Mock response service
        when(responseService.createRedirectResponse("/retro/" + retroId))
            .thenReturn(org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.FOUND)
                .header("HX-Redirect", "/retro/" + retroId).build());

        when(participantService.createAndAssignFacilitatorForSession(eq("Test Session"), eq(null), any(HttpServletRequest.class)))
            .thenReturn(mockParticipant);

        // Act & Assert
        mockMvc.perform(post("/api/retro/create")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionName\":\"Test Session\"}"))
                .andExpect(status().is3xxRedirection());
    }
    
    @Test
    void shouldRequireAuthenticationForSessionCreation() throws Exception {
        // Arrange
        RetroApiController.CreateRetroRequest request = new RetroApiController.CreateRetroRequest("Test Session");

        // Act & Assert - Test unauthenticated access
        mockMvc.perform(post("/api/retro/create")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionName\":\"Test Session\"}"))
                .andExpect(status().isForbidden()); // Should return 403 for API endpoints
    }
    
    @Test
    @WithMockUser(roles = "USER")
    void shouldRequireCSRFToken() throws Exception {
        // Arrange
        RetroApiController.CreateRetroRequest request = new RetroApiController.CreateRetroRequest("Test Session");

        // Act & Assert - Test without CSRF token (should be rejected)
        mockMvc.perform(post("/api/retro/create")
                // No CSRF token provided
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionName\":\"Test Session\"}"))
                .andExpect(status().isForbidden());
    }
}