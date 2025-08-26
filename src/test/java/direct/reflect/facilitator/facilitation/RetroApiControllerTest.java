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

import direct.reflect.facilitator.facilitation.RetroSession;
import direct.reflect.facilitator.facilitation.Participant;
import direct.reflect.facilitator.facilitation.ParticipantRole;
import direct.reflect.facilitator.facilitation.ParticipantService;
import direct.reflect.facilitator.facilitation.RetroSessionService;
import direct.reflect.facilitator.eventing.EventService;
import direct.reflect.facilitator.facilitation.RetroApiController;
import direct.reflect.facilitator.facilitation.dto.CreateRetroRequest;
import direct.reflect.facilitator.facilitation.dto.JoinRetroRequest;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

@WebMvcTest(controllers = {
    RetroApiController.class, 
    direct.reflect.facilitator.auth.AuthController.class
})
@Import({
    direct.reflect.facilitator.common.config.SecurityConfig.class,
    direct.reflect.facilitator.auth.AuthenticationHelper.class
})
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
    private direct.reflect.facilitator.auth.AuthenticationHelper authHelper;
    
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

        CreateRetroRequest request = new CreateRetroRequest("Test Session");

        when(participantService.createAndAssignFacilitatorForSession(eq("Test Session"), any(HttpServletRequest.class)))
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
        JoinRetroRequest request = new JoinRetroRequest(retroId);

        RetroSession mockSession = new RetroSession();
        mockSession.setId(retroId);
        mockSession.setName("Existing Session");

        Participant mockParticipant = new Participant();
        mockParticipant.setParticipantId(UUID.randomUUID()); 
        mockParticipant.setDisplayName("Guest");
        mockParticipant.setSession(mockSession);

        when(retroSessionService.sessionExists(retroId)).thenReturn(true);
        when(retroSessionService.getSessionById(retroId)).thenReturn(mockSession); 
        when(participantService.addParticipantToSession(any(HttpServletRequest.class), eq(mockSession), eq(ParticipantRole.PARTICIPANT)))
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
        CreateRetroRequest request = new CreateRetroRequest("Test Session");
        
        UUID retroId = UUID.randomUUID();
        RetroSession mockSession = new RetroSession();
        mockSession.setId(retroId);
        mockSession.setName("Test Session");
        
        Participant mockParticipant = new Participant();
        mockParticipant.setParticipantId(UUID.randomUUID());
        mockParticipant.setDisplayName("Test Guest");
        mockParticipant.setSession(mockSession);

        when(participantService.createAndAssignFacilitatorForSession(eq("Test Session"), any(HttpServletRequest.class)))
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
        CreateRetroRequest request = new CreateRetroRequest("Test Session");

        // Act & Assert - Test unauthenticated access
        mockMvc.perform(post("/api/retro/create")
                .with(anonymous()) // Explicitly ensure no authentication
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionName\":\"Test Session\"}"))
                .andExpect(status().isUnauthorized()); // Should return 401 for API endpoints with OAuth
    }
    
    @Test
    @WithMockUser(roles = "USER")
    void shouldRequireCSRFToken() throws Exception {
        // Arrange
        CreateRetroRequest request = new CreateRetroRequest("Test Session");

        // Act & Assert - Test without CSRF token (should be rejected)
        mockMvc.perform(post("/api/retro/create")
                // No CSRF token provided
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionName\":\"Test Session\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void guestAuth_ValidDisplayName_ShouldRedirectToHome() throws Exception {
        // Guest authentication should accept POST with displayName and redirect to home
        mockMvc.perform(post("/auth/guest")
                .param("displayName", "Test Guest User")
                .with(csrf()))
                .andExpect(status().isFound()) // Guest auth redirects to /
                .andExpect(redirectedUrl("/"));
    }
    
    @Test
    void guestAuth_EmptyDisplayName_ShouldRedirectToLoginWithError() throws Exception {
        // Guest authentication should reject empty display name
        mockMvc.perform(post("/auth/guest")
                .param("displayName", "")
                .with(csrf()))
                .andExpect(status().isFound()) // Guest auth redirects on error
                .andExpect(redirectedUrl("/login?error=missing_display_name"));
    }
}