package direct.reflect.facilitator.web.api;

import direct.reflect.facilitator.auth.AuthApiController;
import direct.reflect.facilitator.auth.AuthController;
import direct.reflect.facilitator.auth.AuthService;
import direct.reflect.facilitator.configurator.RetroStepQueryService;
import direct.reflect.facilitator.facilitation.participant.ParticipantService;
import direct.reflect.facilitator.facilitation.session.RetroSessionService;
import direct.reflect.facilitator.facilitation.session.RetroSyncVersionService;
import direct.reflect.facilitator.facilitation.session.SessionApiController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {AuthApiController.class, AuthController.class, SessionApiController.class})
@EnableAutoConfiguration(exclude = {
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class
})
@Import({direct.reflect.facilitator.config.TestSecurityOverride.class, ApiExceptionHandler.class})
@ActiveProfiles("test")
public class ApiExceptionHandlerScopingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private RetroSessionService retroSessionService;

    @MockitoBean
    private ParticipantService participantService;

    @MockitoBean
    private RetroStepQueryService retroStepQueryService;

    @MockitoBean
    private RetroSyncVersionService retroSyncVersionService;

    @Test
    void shouldNotInterceptNonApiAuthControllerExceptions() throws Exception {
        doThrow(new IllegalArgumentException("Test exception"))
            .when(authService).initializeGuestSession(any(), eq("Test User"));

        mockMvc.perform(post("/auth/guest")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .param("displayName", "Test User"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?error=guest_auth_failed"));
    }

    @Test
    @WithMockUser
    void shouldInterceptRestControllerExceptions() throws Exception {
        mockMvc.perform(post("/api/retros")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .content("invalid-json")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(status().isBadRequest());
    }
}
