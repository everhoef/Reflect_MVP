package direct.reflect.facilitator.facilitation;

import direct.reflect.facilitator.facilitation.Participant;
import direct.reflect.facilitator.facilitation.RetroSession;
import direct.reflect.facilitator.configurator.RetroTemplate;
import direct.reflect.facilitator.facilitation.ParticipantRole;
import direct.reflect.facilitator.facilitation.ParticipantRepository;
import direct.reflect.facilitator.facilitation.ParticipantService;
import direct.reflect.facilitator.facilitation.RetroSessionService;
import direct.reflect.facilitator.facilitation.RetroPhase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Cookie;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ParticipantServiceTest {

    @Mock
    private ParticipantRepository participantRepository;

    @Mock
    private RetroSessionService retroSessionService;

    @InjectMocks
    private ParticipantService participantService;

    private HttpServletRequest mockRequest;
    private MockedStatic<SecurityContextHolder> securityContextMock;

    @BeforeEach
    void setUp() {
        mockRequest = new MockHttpServletRequest();
        securityContextMock = Mockito.mockStatic(SecurityContextHolder.class);
    }

    @AfterEach
    void tearDown() {
        if (securityContextMock != null) {
            securityContextMock.close();
        }
    }

    @Test
    void createSession_AnonymousUserWithDisplayName_Success() {
        // Arrange
        String sessionName = "Test Session";
        String displayName = "John Doe";
        
        RetroTemplate template = new RetroTemplate();
        template.setId(1L);
        RetroSession session = new RetroSession();
        session.setId(UUID.randomUUID());
        session.setName(sessionName);
        
        // Create standard Spring Security authentication token with GUEST role
        Authentication guestAuth = new UsernamePasswordAuthenticationToken(
            displayName, null, List.of(new SimpleGrantedAuthority("ROLE_GUEST"))
        );
        SecurityContext securityContext = new SecurityContextImpl(guestAuth);
        
        when(retroSessionService.getDefaultTemplate()).thenReturn(template);
        when(retroSessionService.createNewSession(sessionName, template)).thenReturn(session);
        when(participantRepository.findByParticipantIdWithSession(any(UUID.class))).thenReturn(Collections.emptyList());
        when(participantRepository.save(any(Participant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        securityContextMock.when(SecurityContextHolder::getContext).thenReturn(securityContext);

        // Act
        Participant result = participantService.createAndAssignFacilitatorForSession(sessionName, null, mockRequest);
        
        // Assert
        assertEquals(displayName, result.getDisplayName());
        assertNull(result.getUsername());
        assertEquals(ParticipantRole.FACILITATOR, result.getRole());
        assertEquals(session, result.getSession());
        assertNotNull(result.getParticipantId());
    }

    @Test
    void createSession_AuthenticatedUserWithoutDisplayName_UsesUsername() {
        // Arrange
        String sessionName = "Test Session";
        String username = "testuser";
        
        RetroTemplate template = new RetroTemplate();
        template.setId(1L);
        RetroSession session = new RetroSession();
        session.setId(UUID.randomUUID());
        session.setName(sessionName);
        
        SecurityContext securityContext = new SecurityContextImpl(
            new UsernamePasswordAuthenticationToken(username, null, Collections.emptyList())
        );
        
        when(retroSessionService.getDefaultTemplate()).thenReturn(template);
        when(retroSessionService.createNewSession(sessionName, template)).thenReturn(session);
        when(participantRepository.findByParticipantIdWithSession(any(UUID.class))).thenReturn(Collections.emptyList());
        when(participantRepository.save(any(Participant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        securityContextMock.when(SecurityContextHolder::getContext).thenReturn(securityContext);

        // Act
        Participant result = participantService.createAndAssignFacilitatorForSession(sessionName, null, mockRequest);
        
        // Assert
        assertEquals(username, result.getDisplayName()); // Falls back to username
        assertEquals(username, result.getUsername());
        assertEquals(ParticipantRole.FACILITATOR, result.getRole());
    }

    @Test
    void createSession_AnonymousUserWithoutDisplayName_Fails() {
        // Arrange
        String sessionName = "Test Session";
        
        // Create unauthenticated guest token - this would typically result in authentication failure
        Authentication guestAuth = new UsernamePasswordAuthenticationToken(null, null);
        SecurityContext securityContext = new SecurityContextImpl(guestAuth);
        
        securityContextMock.when(SecurityContextHolder::getContext).thenReturn(securityContext);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, 
            () -> participantService.createAndAssignFacilitatorForSession(sessionName, null, mockRequest));
    }

    @Test
    void createSession_UserAlreadyInActiveSession_Fails() {
        // Arrange
        String sessionName = "Test Session";
        String displayName = "John Doe";
        
        RetroSession activeSession = new RetroSession();
        activeSession.setId(UUID.randomUUID());
        activeSession.setPhase(RetroPhase.GATHER_DATA); // Active
        
        Participant existingParticipant = new Participant();
        existingParticipant.setSession(activeSession);
        
        Authentication guestAuth = new UsernamePasswordAuthenticationToken(
            displayName, null, List.of(new SimpleGrantedAuthority("ROLE_GUEST"))
        );
        SecurityContext securityContext = new SecurityContextImpl(guestAuth);
        
        when(participantRepository.findByParticipantIdWithSession(any(UUID.class)))
            .thenReturn(Collections.singletonList(existingParticipant));
        securityContextMock.when(SecurityContextHolder::getContext).thenReturn(securityContext);

        // Act & Assert
        assertThrows(IllegalStateException.class, 
            () -> participantService.createAndAssignFacilitatorForSession(sessionName, null, mockRequest));
    }

    @Test
    void addParticipantToSession_Success() {
        // Arrange
        String displayName = "Jane Doe";
        RetroSession session = new RetroSession();
        session.setId(UUID.randomUUID());
        
        Authentication guestAuth = new UsernamePasswordAuthenticationToken(
            displayName, null, List.of(new SimpleGrantedAuthority("ROLE_GUEST"))
        );
        SecurityContext securityContext = new SecurityContextImpl(guestAuth);
        
        when(participantRepository.findByParticipantIdWithSession(any(UUID.class))).thenReturn(Collections.emptyList());
        when(participantRepository.save(any(Participant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        securityContextMock.when(SecurityContextHolder::getContext).thenReturn(securityContext);

        // Act
        Participant result = participantService.addParticipantToSession(mockRequest, session, null, ParticipantRole.PARTICIPANT);
        
        // Assert
        assertEquals(displayName, result.getDisplayName());
        assertEquals(ParticipantRole.PARTICIPANT, result.getRole());
        assertEquals(session, result.getSession());
    }

    @Test
    void getCurrentParticipant_AnonymousUser_Success() {
        // Arrange
        String displayName = "Anonymous User";
        
        Authentication guestAuth = new UsernamePasswordAuthenticationToken(
            displayName, null, List.of(new SimpleGrantedAuthority("ROLE_GUEST"))
        );
        SecurityContext securityContext = new SecurityContextImpl(guestAuth);
        
        securityContextMock.when(SecurityContextHolder::getContext).thenReturn(securityContext);

        // Act
        Participant result = participantService.getCurrentParticipant(mockRequest, null);
        
        // Assert
        assertEquals(displayName, result.getDisplayName());
        assertNull(result.getUsername());
        assertNotNull(result.getParticipantId());
    }

    @Test
    void getCurrentParticipant_AuthenticatedUser_Success() {
        // Arrange
        String username = "testuser";
        
        SecurityContext securityContext = new SecurityContextImpl(
            new UsernamePasswordAuthenticationToken(username, null, Collections.emptyList())
        );
        securityContextMock.when(SecurityContextHolder::getContext).thenReturn(securityContext);

        // Act
        Participant result = participantService.getCurrentParticipant(mockRequest, null);
        
        // Assert
        assertEquals(username, result.getDisplayName()); // For users, display name = username
        assertEquals(username, result.getUsername());
        assertNotNull(result.getParticipantId());
    }

    @Test
    void getCurrentParticipant_AuthenticatedUserWithoutDisplayName_FallsBackToUsername() {
        // Arrange
        String username = "testuser";
        
        SecurityContext securityContext = new SecurityContextImpl(
            new UsernamePasswordAuthenticationToken(username, null, Collections.emptyList())
        );
        securityContextMock.when(SecurityContextHolder::getContext).thenReturn(securityContext);

        // Act
        Participant result = participantService.getCurrentParticipant(mockRequest, null);
        
        // Assert
        assertEquals(username, result.getDisplayName()); // Falls back to username
        assertEquals(username, result.getUsername());
        assertNotNull(result.getParticipantId());
    }
}