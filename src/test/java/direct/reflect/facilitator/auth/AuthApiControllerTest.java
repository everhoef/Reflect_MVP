package direct.reflect.facilitator.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {AuthApiController.class, AuthController.class})
@Import({direct.reflect.facilitator.config.TestSecurityOverride.class})
@EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
})
@ActiveProfiles("test")
public class AuthApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void shouldReturn401ForUnauthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Authentication required"))
                .andExpect(jsonPath("$.loginUrl").value("/login"));
    }

    @Test
    void shouldReturn200ForGuestUser() throws Exception {
        UUID participantId = UUID.randomUUID();
        String displayName = "Alice";

        when(authService.getParticipantId(any(jakarta.servlet.http.HttpServletRequest.class)))
                .thenReturn(participantId);
        when(authService.getDisplayName(any(jakarta.servlet.http.HttpServletRequest.class)))
                .thenReturn(displayName);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authType", "GUEST");
        session.setAttribute("authenticatedUser", UUID.randomUUID().toString());
        session.setAttribute("userDisplayName", displayName);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                UUID.randomUUID().toString(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_GUEST"))
        );
        SecurityContextImpl securityContext = new SecurityContextImpl(auth);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);

        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.isAuthenticated").value(true))
                .andExpect(jsonPath("$.isGuest").value(true))
                .andExpect(jsonPath("$.authType").value("GUEST"))
                .andExpect(jsonPath("$.user.id").value(participantId.toString()))
                .andExpect(jsonPath("$.user.displayName").value(displayName))
                .andExpect(jsonPath("$.user.role").value("GUEST"));
    }

    @Test
    void shouldRehydrateGuestAuthTypeFromAuthenticatedSession() throws Exception {
        UUID participantId = UUID.randomUUID();
        String displayName = "Alice";

        when(authService.getParticipantId(any(jakarta.servlet.http.HttpServletRequest.class)))
                .thenReturn(participantId);
        when(authService.getDisplayName(any(jakarta.servlet.http.HttpServletRequest.class)))
                .thenReturn(displayName);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUser", UUID.randomUUID().toString());
        session.setAttribute("userDisplayName", displayName);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                UUID.randomUUID().toString(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_GUEST"))
        );
        SecurityContextImpl securityContext = new SecurityContextImpl(auth);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);

        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAuthenticated").value(true))
                .andExpect(jsonPath("$.isGuest").value(true))
                .andExpect(jsonPath("$.authType").value("GUEST"))
                .andExpect(jsonPath("$.user.id").value(participantId.toString()))
                .andExpect(jsonPath("$.user.displayName").value(displayName));
    }

    @Test
    void shouldReturn200ForOidcUser() throws Exception {
        UUID participantId = UUID.randomUUID();
        String displayName = "Bob Smith";
        String username = "bob";

        when(authService.getParticipantId(any(jakarta.servlet.http.HttpServletRequest.class)))
                .thenReturn(participantId);
        when(authService.getDisplayName(any(jakarta.servlet.http.HttpServletRequest.class)))
                .thenReturn(displayName);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authType", "OIDC");
        session.setAttribute("authenticatedUser", username);
        session.setAttribute("userDisplayName", displayName);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                username,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextImpl securityContext = new SecurityContextImpl(auth);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);

        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.isAuthenticated").value(true))
                .andExpect(jsonPath("$.isGuest").value(false))
                .andExpect(jsonPath("$.authType").value("OIDC"))
                .andExpect(jsonPath("$.user.id").value(participantId.toString()))
                .andExpect(jsonPath("$.user.displayName").value(displayName))
                .andExpect(jsonPath("$.user.role").value("USER"));
    }

    @Test
    void guestAuthWithoutCsrfToken_shouldRedirectHomeAndInitializeGuestSession() throws Exception {
        mockMvc.perform(post("/auth/guest")
                        .param("displayName", "Atlas QA"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/"));

        verify(authService).initializeGuestSession(any(jakarta.servlet.http.HttpServletRequest.class), eq("Atlas QA"));
    }
}
