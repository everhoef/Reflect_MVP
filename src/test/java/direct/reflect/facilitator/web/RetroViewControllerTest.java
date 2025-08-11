package direct.reflect.facilitator.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import direct.reflect.facilitator.facilitation.RetroSession;
import direct.reflect.facilitator.configurator.RetroTemplate;
import direct.reflect.facilitator.facilitation.Participant;
import direct.reflect.facilitator.facilitation.RetroPhase;
import direct.reflect.facilitator.common.exception.ParticipantNotFoundException;
import direct.reflect.facilitator.facilitation.RetroSessionService;
import direct.reflect.facilitator.facilitation.ParticipantService;
import direct.reflect.facilitator.web.RetroViewController;

@WebMvcTest(controllers = RetroViewController.class)
@Import({direct.reflect.facilitator.common.config.SecurityConfig.class, direct.reflect.facilitator.common.exception.ViewExceptionHandler.class})
@EnableAutoConfiguration(exclude = {
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class,
    RedisAutoConfiguration.class
})
class RetroViewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RetroSessionService retroService;

    @MockitoBean
    private ParticipantService participantService;
    
    @MockitoBean
    private direct.reflect.facilitator.facilitation.AuthenticationService authenticationService;

    @Test
    @WithMockUser
    void homePage_AuthenticatedUser_ShouldReturnOk() throws Exception {
        // Mock the templates call
        when(retroService.getAvailableTemplates())
            .thenReturn(java.util.Collections.emptyList());

        mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(view().name("layout"))
            .andExpect(model().attribute("page", "home"))
            .andExpect(model().attribute("title", "Team Retrospective - Home"));
    }

    @Test
    void homePage_UnauthenticatedUser_ShouldRedirectToLogin() throws Exception {
        // Unauthenticated users should be redirected to login page
        mockMvc.perform(get("/"))
            .andExpect(status().is3xxRedirection()); // Unauthenticated users get redirected to /login
    }

    @Test
    @WithMockUser
    void retroView_AuthenticatedUser_NotParticipating_ShouldRedirect() throws Exception {
        // Given
        UUID retroId = UUID.randomUUID();
        when(participantService.getParticipantForSession(any(), eq(retroId)))
            .thenThrow(new ParticipantNotFoundException("Participant not found for session: " + retroId));
        when(retroService.getSessionById(retroId)).thenReturn(createMockSession(retroId, RetroPhase.LOBBY));

        // When & Then
        mockMvc.perform(get("/retro/{retroId}", retroId))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    @Test
    void retroView_UnauthenticatedUser_NotParticipating_ShouldRedirect() throws Exception {
        // Given
        UUID retroId = UUID.randomUUID();
        
        // When & Then - unauthenticated users always get redirected to /login
        mockMvc.perform(get("/retro/{retroId}", retroId))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "authenticateduser")
    void retroView_AuthenticatedUser_WithCookie_ShouldWork() throws Exception {
        // Given
        UUID retroId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        RetroSession session = createMockSession(retroId, RetroPhase.LOBBY);
        Participant participant = createMockParticipant();
        participant.setUsername("authenticateduser"); // Should match @WithMockUser
        participant.setDisplayName("Authenticated User");

        // Mock all service calls (now returning blocking values)
        when(participantService.getParticipantForSession(any(), eq(retroId)))
            .thenReturn(participant);
        when(participantService.isFacilitator(any(), eq(retroId)))
            .thenReturn(false);
        when(retroService.getSessionById(retroId)).thenReturn(session);
        when(participantService.getSessionParticipants(retroId))
            .thenReturn(java.util.Collections.emptyList());

        // When & Then - Test with participant cookie
        mockMvc.perform(get("/retro/{retroId}", retroId)
                .cookie(new jakarta.servlet.http.Cookie("participantId", participantId.toString())))
            .andExpect(status().isOk())
            .andExpect(view().name("layout"))
            .andExpect(model().attribute("page", "lobby"));
    }

    @Test
    void retroView_UnauthenticatedUser_WithCookie_ShouldRedirect() throws Exception {
        // Given - unauthenticated users should be redirected regardless of cookies
        UUID retroId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();

        // When & Then - unauthenticated users always get redirected to /login
        mockMvc.perform(get("/retro/{retroId}", retroId)
                .cookie(new jakarta.servlet.http.Cookie("participantId", participantId.toString())))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser
    void retroView_AuthenticatedUser_WithoutCookie_ShouldWork() throws Exception {
        // Given - when there's no cookie, service should generate one
        UUID retroId = UUID.randomUUID();
        RetroSession session = createMockSession(retroId, RetroPhase.LOBBY);
        Participant participant = createMockParticipant();
        participant.setUsername("authenticateduser");
        participant.setDisplayName("Authenticated User");

        // Mock all service calls
        when(participantService.getParticipantForSession(any(), eq(retroId)))
            .thenReturn(participant);
        when(participantService.isFacilitator(any(), eq(retroId)))
            .thenReturn(false);
        when(retroService.getSessionById(retroId)).thenReturn(session);
        when(participantService.getSessionParticipants(retroId))
            .thenReturn(java.util.Collections.emptyList());

        // When & Then - Test without participant cookie (should still work)
        mockMvc.perform(get("/retro/{retroId}", retroId))
            .andExpect(status().isOk())
            .andExpect(view().name("layout"))
            .andExpect(model().attribute("page", "lobby"));
    }

    @Test
    void retroView_UnauthenticatedUser_WithoutCookie_ShouldRedirect() throws Exception {
        // Given - unauthenticated users should be redirected to login
        UUID retroId = UUID.randomUUID();

        // When & Then - unauthenticated users always get redirected to /login
        mockMvc.perform(get("/retro/{retroId}", retroId))
            .andExpect(status().is3xxRedirection());
    }

    @ParameterizedTest
    @EnumSource(RetroPhase.class)
    @WithMockUser
    void retroView_AuthenticatedUser_AllPhases_ShouldWork(RetroPhase phase) throws Exception {
        // Given
        UUID retroId = UUID.randomUUID();
        RetroSession session = createMockSession(retroId, phase);
        Participant participant = createMockParticipant();
        participant.setUsername("authenticateduser");

        // Mock all service calls
        when(participantService.getParticipantForSession(any(), eq(retroId)))
            .thenReturn(participant);
        when(participantService.isFacilitator(any(), eq(retroId)))
            .thenReturn(false);
        when(retroService.getSessionById(retroId)).thenReturn(session);
        when(participantService.getSessionParticipants(retroId))
            .thenReturn(java.util.Collections.emptyList());

        // When & Then
        mockMvc.perform(get("/retro/{retroId}", retroId))
            .andExpect(status().isOk())
            .andExpect(view().name("layout"));
    }

    @ParameterizedTest
    @EnumSource(RetroPhase.class)
    void retroView_UnauthenticatedUser_AllPhases_ShouldRedirect(RetroPhase phase) throws Exception {
        // Given - unauthenticated users should be redirected regardless of phase
        UUID retroId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();

        // When & Then - unauthenticated users always get redirected to /login
        mockMvc.perform(get("/retro/{retroId}", retroId)
                .cookie(new jakarta.servlet.http.Cookie("participantId", participantId.toString())))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser
    void retroView_AuthenticatedFacilitator_ShouldHaveFacilitatorAccess() throws Exception {
        // Given
        UUID retroId = UUID.randomUUID();
        RetroSession session = createMockSession(retroId, RetroPhase.LOBBY);
        Participant facilitator = createMockParticipant();
        facilitator.setUsername("facilitatoruser");
        facilitator.setDisplayName("Facilitator User");

        // Mock all service calls - this time as facilitator
        when(participantService.getParticipantForSession(any(), eq(retroId)))
            .thenReturn(facilitator);
        when(participantService.isFacilitator(any(), eq(retroId)))
            .thenReturn(true); // This user IS the facilitator
        when(retroService.getSessionById(retroId)).thenReturn(session);
        when(participantService.getSessionParticipants(retroId))
            .thenReturn(java.util.Collections.emptyList());

        // When & Then
        mockMvc.perform(get("/retro/{retroId}", retroId))
            .andExpect(status().isOk())
            .andExpect(view().name("layout"))
            .andExpect(model().attribute("page", "lobby"));
    }

    @Test
    @WithMockUser
    void retroView_AuthenticatedUser_ParticipantNotFound_ShouldRedirect() throws Exception {
        // Given
        UUID retroId = UUID.randomUUID();
        RetroSession session = createMockSession(retroId, RetroPhase.LOBBY);
        
        // Mock that getParticipantForSession throws exception
        when(participantService.getParticipantForSession(any(), eq(retroId)))
            .thenThrow(new ParticipantNotFoundException("Participant not found for session: " + retroId));
        when(retroService.getSessionById(retroId)).thenReturn(session);

        // When & Then
        mockMvc.perform(get("/retro/{retroId}", retroId))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser
    void getParticipantsList_AuthenticatedUser_ShouldReturnOk() throws Exception {
        // Given
        UUID retroId = UUID.randomUUID();
        when(participantService.getSessionParticipants(retroId))
            .thenReturn(java.util.Collections.emptyList());

        // When & Then
        mockMvc.perform(get("/retro/{retroId}/participants", retroId))
            .andExpect(status().isOk());
    }

    @Test
    void getParticipantsList_UnauthenticatedUser_ShouldRedirect() throws Exception {
        // Given - unauthenticated users should be redirected
        UUID retroId = UUID.randomUUID();

        // When & Then - unauthenticated users get redirected to /login
        mockMvc.perform(get("/retro/{retroId}/participants", retroId))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    void loginPage_UnauthenticatedUser_ShouldReturnOk() throws Exception {
        // Login page is public and should work for unauthenticated users
        mockMvc.perform(get("/login"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void loginPage_AuthenticatedUser_ShouldReturnOk() throws Exception {
        // Login page should also work for authenticated users
        mockMvc.perform(get("/login"))
            .andExpect(status().isOk());
    }

    // Helper methods
    private RetroSession createMockSession(UUID retroId, RetroPhase phase) {
        RetroSession session = new RetroSession();
        session.setId(retroId);
        session.setName("Test Session");
        session.setPhase(phase);
        session.setCreatedAt(LocalDateTime.now());
        if (phase == RetroPhase.COMPLETED) {
            session.setFinishedAt(LocalDateTime.now());
        }

        // Create minimal template
        RetroTemplate template = new RetroTemplate();
        template.setId(1L);
        template.setName("Test Template");
        session.setTemplate(template);

        return session;
    }

    private Participant createMockParticipant() {
        Participant participant = new Participant();
        participant.setParticipantId(UUID.randomUUID());
        participant.setDisplayName("Test User");
        participant.setUsername("testuser");
        return participant;
    }
}
