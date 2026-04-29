package direct.reflect.facilitator.facilitation.participant;

import direct.reflect.facilitator.facilitation.participant.Participant;
import direct.reflect.facilitator.facilitation.participant.ParticipantId;
import direct.reflect.facilitator.facilitation.session.RetroSession;
import direct.reflect.facilitator.configurator.RetroTemplate;
import direct.reflect.facilitator.facilitation.participant.ParticipantRole;
import direct.reflect.facilitator.facilitation.participant.ParticipantRepository;
import direct.reflect.facilitator.facilitation.participant.ParticipantService;
import direct.reflect.facilitator.facilitation.participant.ParticipantStatus;
import direct.reflect.facilitator.facilitation.session.RetroPhase;
import direct.reflect.facilitator.facilitation.session.RetroSyncVersionService;
import direct.reflect.facilitator.auth.AuthService;
import direct.reflect.facilitator.eventing.EventService;
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
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ParticipantServiceTest {

    @Mock
    private ParticipantRepository participantRepository;

    @Mock
    private EventService eventService;

    @Mock
    private AuthService authHelper;

    @Mock
    private RetroSyncVersionService retroSyncVersionService;

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

        when(participantRepository.findByParticipantIdAndStatusWithSession(participantId, ParticipantStatus.ACTIVE))
            .thenReturn(Collections.emptyList()); // No active sessions
        when(participantRepository.findById(any(ParticipantId.class)))
            .thenReturn(Optional.empty()); // No existing participant in this session
        when(participantRepository.save(any(Participant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Participant result = participantService.addParticipantToSession(mockRequest, session, ParticipantRole.PARTICIPANT);

        // Assert
        assertEquals(displayName, result.getDisplayName());
        assertEquals(ParticipantRole.PARTICIPANT, result.getRole());
        assertEquals(session, result.getSession());
        assertEquals(participantId, result.getParticipantId());
        verify(retroSyncVersionService).bumpSyncVersion(session.getId());
        verify(eventService).publish(any()); // PARTICIPANT_JOINED event
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

        when(participantRepository.findByParticipantIdAndStatusWithSession(participantId, ParticipantStatus.ACTIVE))
            .thenReturn(List.of(existingParticipant));
        ParticipantId pk = new ParticipantId(participantId, sessionId);
        when(participantRepository.findById(pk))
            .thenReturn(Optional.of(existingParticipant)); // Already exists in this session
        when(participantRepository.save(any(Participant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Participant result = participantService.addParticipantToSession(mockRequest, session, ParticipantRole.PARTICIPANT);

        // Assert
        assertNotNull(result);
        assertEquals(displayName, result.getDisplayName());
        assertEquals(ParticipantRole.PARTICIPANT, result.getRole());
        assertEquals(sessionId, result.getSession().getId());

        // Verify participant was updated (not deleted, no LEFT status)
        verify(participantRepository).save(argThat(p ->
            p.getStatus() != ParticipantStatus.LEFT
        ));
        verify(retroSyncVersionService).bumpSyncVersion(sessionId);
        verify(eventService).publish(any()); // PARTICIPANT_JOINED event
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

        when(participantRepository.findByParticipantIdAndStatusWithSession(participantId, ParticipantStatus.ACTIVE))
            .thenReturn(List.of(existingParticipant));
        when(participantRepository.findById(any(ParticipantId.class)))
            .thenReturn(Optional.empty()); // No existing participant in new session
        when(participantRepository.save(any(Participant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Participant result = participantService.addParticipantToSession(mockRequest, newSession, ParticipantRole.PARTICIPANT);

        // Assert
        assertNotNull(result);
        assertEquals(displayName, result.getDisplayName());
        assertEquals(ParticipantRole.PARTICIPANT, result.getRole());
        assertEquals(newSessionId, result.getSession().getId());
        assertEquals(participantId, result.getParticipantId());

        // Verify old session participant was marked as LEFT and new one created
        verify(participantRepository, times(2)).save(argThat(p -> {
            // Either LEFT status (old participant) or ACTIVE status (new participant)
            return p.getStatus() == ParticipantStatus.LEFT || p.getStatus() == ParticipantStatus.ACTIVE;
        }));
        verify(retroSyncVersionService).bumpSyncVersion(oldSessionId);
        verify(retroSyncVersionService).bumpSyncVersion(newSessionId);
        verify(eventService, times(2)).publish(any()); // PARTICIPANT_LEFT + PARTICIPANT_JOINED events
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

        when(participantRepository.findByParticipantIdAndStatusWithSession(participantId, ParticipantStatus.ACTIVE))
            .thenReturn(List.of(participant1, participant2));
        when(participantRepository.save(any(Participant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        participantService.leaveAllActiveSessions(mockRequest);

        // Assert - Verify participants were marked as LEFT (not deleted)
        verify(participantRepository, times(2)).save(argThat(p ->
            p.getStatus() == ParticipantStatus.LEFT
        ));
        verify(retroSyncVersionService).bumpSyncVersion(session1.getId());
        verify(retroSyncVersionService).bumpSyncVersion(session2.getId());
        verify(eventService, times(2)).publish(any()); // PARTICIPANT_LEFT events

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
        activeParticipant.setStatus(ParticipantStatus.ACTIVE);

        Participant finishedParticipant = new Participant();
        finishedParticipant.setParticipantId(participantId);
        finishedParticipant.setSession(finishedSession);
        finishedParticipant.setStatus(ParticipantStatus.ACTIVE);

        when(participantRepository.findByParticipantIdAndStatusWithSession(participantId, ParticipantStatus.ACTIVE))
            .thenReturn(List.of(activeParticipant, finishedParticipant));

        // Act
        List<Participant> result = participantService.getActiveSessionsForParticipant(participantId);

        // Assert - Only active session (not finished session)
        assertEquals(1, result.size());
        assertEquals(activeSession.getId(), result.get(0).getSession().getId());
        assertFalse(result.get(0).getSession().isFinished());
    }


}
