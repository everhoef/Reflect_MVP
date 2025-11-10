package direct.reflect.facilitator.facilitation;

import direct.reflect.facilitator.facilitation.Participant;
import direct.reflect.facilitator.facilitation.RetroSession;
import direct.reflect.facilitator.configurator.RetroTemplate;
import direct.reflect.facilitator.facilitation.ParticipantRole;
import direct.reflect.facilitator.facilitation.ParticipantRepository;
import direct.reflect.facilitator.facilitation.ParticipantService;
import direct.reflect.facilitator.facilitation.RetroSessionService;
import direct.reflect.facilitator.facilitation.RetroPhase;
import direct.reflect.facilitator.auth.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

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
    
    @Mock
    private AuthService authHelper;

    @InjectMocks
    private ParticipantService participantService;

    private MockHttpServletRequest mockRequest;

    @BeforeEach
    void setUp() {
        mockRequest = new MockHttpServletRequest();
        // Create session for the mock request
        mockRequest.getSession(true);
    }

    @Test
    void createSession_AnonymousUserWithDisplayName_Success() {
        // Arrange
        String sessionName = "Test Session";
        String displayName = "John Doe";
        UUID guestParticipantId = UUID.randomUUID();
        
        RetroTemplate template = new RetroTemplate();
        template.setId(1L);
        RetroSession session = new RetroSession();
        session.setId(UUID.randomUUID());
        session.setName(sessionName);
        
        // Mock AuthService for guest user
        when(authHelper.getParticipantId(mockRequest)).thenReturn(guestParticipantId);
        when(authHelper.getDisplayName(mockRequest)).thenReturn(displayName);
        when(authHelper.getUsername(mockRequest)).thenReturn(null); // Guests don't have usernames
        
        when(retroSessionService.createNewSession(sessionName)).thenReturn(session);
        when(participantRepository.findByParticipantIdWithSession(any(UUID.class))).thenReturn(Collections.emptyList());
        when(participantRepository.save(any(Participant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Participant result = participantService.createAndAssignFacilitatorForSession(sessionName, mockRequest);
        
        // Assert
        assertEquals(displayName, result.getDisplayName());
        assertNull(result.getUsername());
        assertEquals(ParticipantRole.FACILITATOR, result.getRole());
        assertEquals(session, result.getSession());
        assertEquals(guestParticipantId, result.getParticipantId());
    }

    @Test
    void createSession_AuthenticatedUserWithoutDisplayName_UsesUsername() {
        // Arrange
        String sessionName = "Test Session";
        String username = "testuser";
        String displayName = "Test User"; // OIDC users have display names from claims
        UUID userParticipantId = UUID.nameUUIDFromBytes(("6ba7b810-9dad-11d1-80b4-00c04fd430c8" + username).getBytes());
        
        RetroTemplate template = new RetroTemplate();
        template.setId(1L);
        RetroSession session = new RetroSession();
        session.setId(UUID.randomUUID());
        session.setName(sessionName);
        
        // Mock AuthService for OIDC user
        when(authHelper.getParticipantId(mockRequest)).thenReturn(userParticipantId);
        when(authHelper.getDisplayName(mockRequest)).thenReturn(displayName);
        when(authHelper.getUsername(mockRequest)).thenReturn(username);
        
        when(retroSessionService.createNewSession(sessionName)).thenReturn(session);
        when(participantRepository.findByParticipantIdWithSession(any(UUID.class))).thenReturn(Collections.emptyList());
        when(participantRepository.save(any(Participant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Participant result = participantService.createAndAssignFacilitatorForSession(sessionName, mockRequest);
        
        // Assert
        assertEquals(displayName, result.getDisplayName());
        assertEquals(username, result.getUsername());
        assertEquals(ParticipantRole.FACILITATOR, result.getRole());
        assertEquals(userParticipantId, result.getParticipantId());
    }

    @Test
    void createSession_AnonymousUserWithoutDisplayName_Fails() {
        // Arrange
        String sessionName = "Test Session";
        UUID guestParticipantId = UUID.randomUUID();
        
        // Mock AuthService to throw exception when display name is missing
        when(authHelper.getParticipantId(mockRequest)).thenReturn(guestParticipantId);
        when(authHelper.getDisplayName(mockRequest)).thenThrow(new IllegalStateException("Guest session missing guestDisplayName - call initializeGuestSession first"));

        // Act & Assert
        assertThrows(IllegalStateException.class, 
            () -> participantService.createAndAssignFacilitatorForSession(sessionName, mockRequest));
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
        
        // Mock AuthService for guest user
        when(authHelper.getParticipantId(mockRequest)).thenReturn(participantId);
        when(authHelper.getDisplayName(mockRequest)).thenReturn(displayName);
        when(authHelper.getUsername(mockRequest)).thenReturn(null); // Guest user
        
        when(retroSessionService.createNewSession(sessionName)).thenReturn(newSession);
        when(participantRepository.findByParticipantIdWithSession(participantId))
            .thenReturn(List.of(existingParticipant));
        when(participantRepository.save(any(Participant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Participant result = participantService.createAndAssignFacilitatorForSession(sessionName, mockRequest);
        
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
        UUID participantId = UUID.randomUUID();
        RetroSession session = new RetroSession();
        session.setId(UUID.randomUUID());
        
        // Mock AuthService for guest user
        when(authHelper.getParticipantId(mockRequest)).thenReturn(participantId);
        when(authHelper.getDisplayName(mockRequest)).thenReturn(displayName);
        when(authHelper.getUsername(mockRequest)).thenReturn(null); // Guest user
        
        when(participantRepository.findByParticipantIdWithSession(any(UUID.class))).thenReturn(Collections.emptyList());
        when(participantRepository.save(any(Participant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Participant result = participantService.addParticipantToSession(mockRequest, session, ParticipantRole.PARTICIPANT);
        
        // Assert
        assertEquals(displayName, result.getDisplayName());
        assertEquals(ParticipantRole.PARTICIPANT, result.getRole());
        assertEquals(session, result.getSession());
        assertEquals(participantId, result.getParticipantId());
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
        
        // Mock AuthService for guest user
        when(authHelper.getParticipantId(mockRequest)).thenReturn(participantId);
        when(authHelper.getDisplayName(mockRequest)).thenReturn(displayName);
        when(authHelper.getUsername(mockRequest)).thenReturn(null); // Guest user
        
        when(participantRepository.findByParticipantIdWithSession(participantId))
            .thenReturn(List.of(existingParticipant));
        when(participantRepository.save(any(Participant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Participant result = participantService.addParticipantToSession(mockRequest, session, ParticipantRole.PARTICIPANT);
        
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
        
        // Mock AuthService for guest user
        when(authHelper.getParticipantId(mockRequest)).thenReturn(participantId);
        when(authHelper.getDisplayName(mockRequest)).thenReturn(displayName);
        when(authHelper.getUsername(mockRequest)).thenReturn(null); // Guest user
        
        when(participantRepository.findByParticipantIdWithSession(participantId))
            .thenReturn(List.of(existingParticipant));
        when(participantRepository.save(any(Participant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Participant result = participantService.addParticipantToSession(mockRequest, newSession, ParticipantRole.PARTICIPANT);
        
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
        
        // Set up session attributes in mock request
        HttpSession session = mockRequest.getSession();
        session.setAttribute("retroId", session1.getId());
        session.setAttribute("participantRole", "PARTICIPANT");
        session.setAttribute("participantId", participantId);
        
        // Mock AuthService for guest user
        when(authHelper.getParticipantId(mockRequest)).thenReturn(participantId);
        
        when(participantRepository.findByParticipantIdWithSession(participantId))
            .thenReturn(List.of(participant1, participant2));

        // Act
        participantService.leaveAllActiveSessions(mockRequest);
        
        // Assert
        verify(participantRepository).delete(participant1);
        verify(participantRepository).delete(participant2);
        
        // Verify session attributes were cleared
        assertNull(mockRequest.getSession().getAttribute("retroId"));
        assertNull(mockRequest.getSession().getAttribute("participantRole"));
        assertNull(mockRequest.getSession().getAttribute("participantId"));
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


}