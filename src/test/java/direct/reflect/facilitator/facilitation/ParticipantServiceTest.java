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
    void createSession_UserAlreadyInActiveSession_TerminatesOldSession() {
        // Arrange
        String sessionName = "New Session";
        String displayName = "John Doe";
        UUID participantId = UUID.randomUUID();
        
        RetroTemplate template = new RetroTemplate();
        template.setId(1L);
        RetroSession newSession = new RetroSession();
        newSession.setId(UUID.randomUUID());
        newSession.setName(sessionName);
        
        RetroSession activeSession = new RetroSession();
        activeSession.setId(UUID.randomUUID());
        activeSession.setPhase(RetroPhase.GATHER_DATA); // Active
        activeSession.setName("Old Session");
        
        Participant existingParticipant = new Participant();
        existingParticipant.setParticipantId(participantId);
        existingParticipant.setSession(activeSession);
        existingParticipant.setDisplayName(displayName);
        existingParticipant.setRole(ParticipantRole.FACILITATOR);
        
        // Mock guest authentication with session containing guestId
        MockHttpServletRequest mockRequestWithSession = new MockHttpServletRequest();
        mockRequestWithSession.getSession().setAttribute("guestId", participantId);
        
        Authentication guestAuth = new UsernamePasswordAuthenticationToken(
            displayName, null, List.of(new SimpleGrantedAuthority("ROLE_GUEST"))
        );
        SecurityContext securityContext = new SecurityContextImpl(guestAuth);
        
        when(retroSessionService.getDefaultTemplate()).thenReturn(template);
        when(retroSessionService.createNewSession(sessionName, template)).thenReturn(newSession);
        when(participantRepository.findByParticipantIdWithSession(participantId))
            .thenReturn(List.of(existingParticipant));
        when(participantRepository.save(any(Participant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        securityContextMock.when(SecurityContextHolder::getContext).thenReturn(securityContext);

        // Act
        Participant result = participantService.createAndAssignFacilitatorForSession(sessionName, null, mockRequestWithSession);
        
        // Assert
        assertNotNull(result);
        assertEquals(displayName, result.getDisplayName());
        assertEquals(ParticipantRole.FACILITATOR, result.getRole());
        assertEquals(newSession, result.getSession());
        assertEquals(participantId, result.getParticipantId());
        
        // Verify old session participant was deleted
        verify(participantRepository).delete(existingParticipant);
        verify(participantRepository).save(any(Participant.class));
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

    @Test
    void addParticipantToSession_SameSessionRejoin_AllowsRejoin() {
        // Arrange
        String displayName = "John Doe";
        UUID participantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        
        RetroSession session = new RetroSession();
        session.setId(sessionId);
        session.setPhase(RetroPhase.LOBBY);
        
        Participant existingParticipant = new Participant();
        existingParticipant.setParticipantId(participantId);
        existingParticipant.setSession(session);
        existingParticipant.setDisplayName(displayName);
        existingParticipant.setRole(ParticipantRole.PARTICIPANT);
        
        MockHttpServletRequest mockRequestWithSession = new MockHttpServletRequest();
        mockRequestWithSession.getSession().setAttribute("guestId", participantId);
        
        Authentication guestAuth = new UsernamePasswordAuthenticationToken(
            displayName, null, List.of(new SimpleGrantedAuthority("ROLE_GUEST"))
        );
        SecurityContext securityContext = new SecurityContextImpl(guestAuth);
        
        when(participantRepository.findByParticipantIdWithSession(participantId))
            .thenReturn(List.of(existingParticipant));
        when(participantRepository.save(any(Participant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        securityContextMock.when(SecurityContextHolder::getContext).thenReturn(securityContext);

        // Act
        Participant result = participantService.addParticipantToSession(mockRequestWithSession, session, null, ParticipantRole.PARTICIPANT);
        
        // Assert
        assertNotNull(result);
        assertEquals(displayName, result.getDisplayName());
        assertEquals(ParticipantRole.PARTICIPANT, result.getRole());
        assertEquals(sessionId, result.getSession().getId());
        
        // Verify no deletion occurred (same session rejoin)
        verify(participantRepository, never()).delete(any(Participant.class));
        verify(participantRepository).save(any(Participant.class));
    }

    @Test
    void addParticipantToSession_DifferentSessionSwitch_TerminatesOldSession() {
        // Arrange
        String displayName = "John Doe";
        UUID participantId = UUID.randomUUID();
        UUID oldSessionId = UUID.randomUUID();
        UUID newSessionId = UUID.randomUUID();
        
        RetroSession oldSession = new RetroSession();
        oldSession.setId(oldSessionId);
        oldSession.setPhase(RetroPhase.GATHER_DATA);
        oldSession.setName("Old Session");
        
        RetroSession newSession = new RetroSession();
        newSession.setId(newSessionId);
        newSession.setPhase(RetroPhase.LOBBY);
        newSession.setName("New Session");
        
        Participant existingParticipant = new Participant();
        existingParticipant.setParticipantId(participantId);
        existingParticipant.setSession(oldSession);
        existingParticipant.setDisplayName(displayName);
        existingParticipant.setRole(ParticipantRole.PARTICIPANT);
        
        MockHttpServletRequest mockRequestWithSession = new MockHttpServletRequest();
        mockRequestWithSession.getSession().setAttribute("guestId", participantId);
        
        Authentication guestAuth = new UsernamePasswordAuthenticationToken(
            displayName, null, List.of(new SimpleGrantedAuthority("ROLE_GUEST"))
        );
        SecurityContext securityContext = new SecurityContextImpl(guestAuth);
        
        when(participantRepository.findByParticipantIdWithSession(participantId))
            .thenReturn(List.of(existingParticipant));
        when(participantRepository.save(any(Participant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        securityContextMock.when(SecurityContextHolder::getContext).thenReturn(securityContext);

        // Act
        Participant result = participantService.addParticipantToSession(mockRequestWithSession, newSession, null, ParticipantRole.PARTICIPANT);
        
        // Assert
        assertNotNull(result);
        assertEquals(displayName, result.getDisplayName());
        assertEquals(ParticipantRole.PARTICIPANT, result.getRole());
        assertEquals(newSessionId, result.getSession().getId());
        assertEquals(participantId, result.getParticipantId());
        
        // Verify old session participant was deleted and new one created
        verify(participantRepository).delete(existingParticipant);
        verify(participantRepository).save(any(Participant.class));
    }

    @Test
    void leaveAllActiveSessions_Success() {
        // Arrange
        String displayName = "John Doe";
        UUID participantId = UUID.randomUUID();
        
        RetroSession session1 = new RetroSession();
        session1.setId(UUID.randomUUID());
        session1.setPhase(RetroPhase.LOBBY);
        session1.setName("Session 1");
        
        RetroSession session2 = new RetroSession();
        session2.setId(UUID.randomUUID());
        session2.setPhase(RetroPhase.GATHER_DATA);
        session2.setName("Session 2");
        
        Participant participant1 = new Participant();
        participant1.setParticipantId(participantId);
        participant1.setSession(session1);
        participant1.setDisplayName(displayName);
        
        Participant participant2 = new Participant();
        participant2.setParticipantId(participantId);
        participant2.setSession(session2);
        participant2.setDisplayName(displayName);
        
        MockHttpServletRequest mockRequestWithSession = new MockHttpServletRequest();
        mockRequestWithSession.getSession().setAttribute("guestId", participantId);
        mockRequestWithSession.getSession().setAttribute("retroId", session1.getId());
        mockRequestWithSession.getSession().setAttribute("participantRole", "PARTICIPANT");
        mockRequestWithSession.getSession().setAttribute("participantId", participantId);
        
        Authentication guestAuth = new UsernamePasswordAuthenticationToken(
            displayName, null, List.of(new SimpleGrantedAuthority("ROLE_GUEST"))
        );
        SecurityContext securityContext = new SecurityContextImpl(guestAuth);
        
        when(participantRepository.findByParticipantIdWithSession(participantId))
            .thenReturn(List.of(participant1, participant2));
        securityContextMock.when(SecurityContextHolder::getContext).thenReturn(securityContext);

        // Act
        participantService.leaveAllActiveSessions(mockRequestWithSession);
        
        // Assert
        verify(participantRepository).delete(participant1);
        verify(participantRepository).delete(participant2);
        
        // Verify session attributes were cleared
        assertNull(mockRequestWithSession.getSession().getAttribute("retroId"));
        assertNull(mockRequestWithSession.getSession().getAttribute("participantRole"));
        assertNull(mockRequestWithSession.getSession().getAttribute("participantId"));
    }

    @Test
    void getActiveSessionsForParticipant_FiltersFinishedSessions() {
        // Arrange
        UUID participantId = UUID.randomUUID();
        
        RetroSession activeSession = new RetroSession();
        activeSession.setId(UUID.randomUUID());
        activeSession.setPhase(RetroPhase.GATHER_DATA); // Not finished
        
        RetroSession finishedSession = new RetroSession();
        finishedSession.setId(UUID.randomUUID());
        finishedSession.setPhase(RetroPhase.COMPLETED); // Finished
        
        Participant activeParticipant = new Participant();
        activeParticipant.setParticipantId(participantId);
        activeParticipant.setSession(activeSession);
        
        Participant finishedParticipant = new Participant();
        finishedParticipant.setParticipantId(participantId);
        finishedParticipant.setSession(finishedSession);
        
        when(participantRepository.findByParticipantIdWithSession(participantId))
            .thenReturn(List.of(activeParticipant, finishedParticipant));

        // Act
        List<Participant> result = participantService.getActiveSessionsForParticipant(participantId);
        
        // Assert
        assertEquals(1, result.size());
        assertEquals(activeSession.getId(), result.get(0).getSession().getId());
        assertFalse(result.get(0).getSession().isFinished());
    }

    @Test
    void guestParticipantId_ConsistentAcrossRequests() {
        // Arrange
        String displayName = "Guest User";
        UUID guestId = UUID.randomUUID();
        
        MockHttpServletRequest mockRequestWithSession = new MockHttpServletRequest();
        mockRequestWithSession.getSession().setAttribute("guestId", guestId);
        
        Authentication guestAuth = new UsernamePasswordAuthenticationToken(
            displayName, null, List.of(new SimpleGrantedAuthority("ROLE_GUEST"))
        );
        SecurityContext securityContext = new SecurityContextImpl(guestAuth);
        securityContextMock.when(SecurityContextHolder::getContext).thenReturn(securityContext);

        // Act - call twice to verify consistency
        Participant result1 = participantService.getCurrentParticipant(mockRequestWithSession, null);
        Participant result2 = participantService.getCurrentParticipant(mockRequestWithSession, null);
        
        // Assert
        assertEquals(guestId, result1.getParticipantId());
        assertEquals(guestId, result2.getParticipantId());
        assertEquals(result1.getParticipantId(), result2.getParticipantId());
    }

    @Test
    void userParticipantId_ConsistentAcrossRequests() {
        // Arrange
        String username = "testuser";
        
        SecurityContext securityContext = new SecurityContextImpl(
            new UsernamePasswordAuthenticationToken(username, null, Collections.emptyList())
        );
        securityContextMock.when(SecurityContextHolder::getContext).thenReturn(securityContext);

        // Act - call twice to verify consistency
        Participant result1 = participantService.getCurrentParticipant(mockRequest, null);
        Participant result2 = participantService.getCurrentParticipant(mockRequest, null);
        
        // Assert
        assertNotNull(result1.getParticipantId());
        assertNotNull(result2.getParticipantId());
        assertEquals(result1.getParticipantId(), result2.getParticipantId());
        assertEquals(username, result1.getUsername());
        assertEquals(username, result2.getUsername());
    }
}