package direct.reflect.facilitator.facilitation;

import direct.reflect.facilitator.configurator.RetroTemplate;
import direct.reflect.facilitator.configurator.RetroTemplateService;
import direct.reflect.facilitator.configurator.RetroStepRepository;
import direct.reflect.facilitator.configurator.RetroStage;
import direct.reflect.facilitator.configurator.RetroStep;
import direct.reflect.facilitator.configurator.ComponentType;
import direct.reflect.facilitator.configurator.AdvancementTrigger;
import direct.reflect.facilitator.facilitation.response.ParticipantResponseRepository;
import direct.reflect.facilitator.eventing.EventService;
import direct.reflect.facilitator.eventing.RetroEvent;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RetroSessionServiceTest {

    @Mock
    private RetroSessionRepository sessionRepository;

    @Mock
    private RetroTemplateService retroTemplateService;

    @Mock
    private RetroStepRepository stepRepository;

    @Mock
    private ParticipantResponseRepository responseRepository;

    @Mock
    private ParticipantService participantService;

    @Mock
    private EventService eventService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private RetroSessionService retroSessionService;

    @Test
    void createNewSession_CreatesSessionInLobbyPhase() {
        // Arrange
        String sessionName = "Test Retrospective";
        RetroTemplate template = new RetroTemplate();
        template.setId(1L);
        template.setName("Default Template");

        when(retroTemplateService.selectTemplateForSession()).thenReturn(template);
        when(sessionRepository.save(any(RetroSession.class))).thenAnswer(invocation -> {
            RetroSession session = invocation.getArgument(0);
            session.setId(UUID.randomUUID());
            return session;
        });

        // Act
        RetroSession result = retroSessionService.createNewSession(sessionName);

        // Assert
        assertNotNull(result);
        assertEquals(sessionName, result.getName());
        assertEquals(RetroPhase.LOBBY, result.getPhase());
        assertEquals(template, result.getTemplate());
        assertNotNull(result.getCreatedAt());

        verify(sessionRepository).save(any(RetroSession.class));
        verify(eventService).publish(any(RetroEvent.class)); // RETRO_CREATED event
    }

    @Test
    void startSession_AdvancesToSetTheStagePhaseAndStartsFirstStep() {
        // Arrange
        UUID sessionId = UUID.randomUUID();
        RetroSession session = new RetroSession();
        session.setId(sessionId);
        session.setPhase(RetroPhase.LOBBY);
        session.setCurrentStepIndex(-1);

        RetroTemplate template = new RetroTemplate();
        RetroStage setTheStage = new RetroStage();
        setTheStage.setId(1L);
        setTheStage.setName("Set the Stage");
        template.setSetTheStage(setTheStage);
        session.setTemplate(template);

        RetroStep firstStep = new RetroStep();
        firstStep.setId(1L);
        firstStep.setComponentType(ComponentType.MULTI_COLUMN_BOARD);

        // Mock repository calls
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(RetroSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(stepRepository.findByRetroStageOrderByOrderIndexAsc(any(RetroStage.class))).thenReturn(List.of(firstStep));

        // Act
        retroSessionService.startSession(sessionId);

        // Assert - Verify phase transition and step initialization
        assertEquals(RetroPhase.SET_THE_STAGE, session.getPhase());
        assertEquals(0, session.getCurrentStepIndex());
        verify(sessionRepository, times(2)).save(any(RetroSession.class));
    }

    @Test
    void getCurrentStep_ReturnsCorrectStepForSessionState() {
        // Arrange
        UUID sessionId = UUID.randomUUID();
        RetroSession session = new RetroSession();
        session.setId(sessionId);
        session.setPhase(RetroPhase.SET_THE_STAGE);
        session.setCurrentStepIndex(1);

        RetroTemplate template = new RetroTemplate();
        RetroStage setTheStage = new RetroStage();
        setTheStage.setId(1L);
        template.setSetTheStage(setTheStage);
        session.setTemplate(template);

        RetroStep step1 = new RetroStep();
        step1.setId(1L);
        step1.setOrderIndex(0);
        step1.setComponentType(ComponentType.MULTI_COLUMN_BOARD);

        RetroStep step2 = new RetroStep();
        step2.setId(2L);
        step2.setOrderIndex(1);
        step2.setComponentType(ComponentType.RATING_SCALE);

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(stepRepository.findByRetroStageOrderByOrderIndexAsc(setTheStage))
            .thenReturn(List.of(step1, step2));

        // Act
        RetroStep result = retroSessionService.getCurrentStep(sessionId);

        // Assert
        assertNotNull(result);
        assertEquals(2L, result.getId());
        assertEquals(ComponentType.RATING_SCALE, result.getComponentType());
    }

    @Test
    void advanceToNextStep_WithinSameStage_IncrementsStepIndex() {
        // Arrange
        UUID sessionId = UUID.randomUUID();
        RetroSession session = new RetroSession();
        session.setId(sessionId);
        session.setPhase(RetroPhase.SET_THE_STAGE);
        session.setCurrentStepIndex(0);

        RetroTemplate template = new RetroTemplate();
        RetroStage setTheStage = new RetroStage();
        setTheStage.setId(1L);
        template.setSetTheStage(setTheStage);
        session.setTemplate(template);

        RetroStep step1 = new RetroStep();
        step1.setId(1L);
        RetroStep step2 = new RetroStep();
        step2.setId(2L);

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(RetroSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stepRepository.findByRetroStageOrderByOrderIndexAsc(setTheStage))
            .thenReturn(List.of(step1, step2));

        // Act
        retroSessionService.advanceToNextStep(sessionId);

        // Assert
        verify(sessionRepository).save(argThat(s ->
            s.getCurrentStepIndex() == 1 &&
            s.getPhase() == RetroPhase.SET_THE_STAGE
        ));
    }

    @Test
    void advanceToNextStep_AtEndOfStage_AdvancesToNextPhase() {
        // Arrange
        UUID sessionId = UUID.randomUUID();
        RetroSession session = new RetroSession();
        session.setId(sessionId);
        session.setPhase(RetroPhase.SET_THE_STAGE);
        session.setCurrentStepIndex(0); // Only one step in this stage

        RetroTemplate template = new RetroTemplate();
        RetroStage setTheStage = new RetroStage();
        setTheStage.setId(1L);
        template.setSetTheStage(setTheStage);

        RetroStage gatherData = new RetroStage();
        gatherData.setId(2L);
        template.setGatherData(gatherData);

        session.setTemplate(template);

        RetroStep step1 = new RetroStep();
        step1.setId(1L);

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(RetroSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stepRepository.findByRetroStageOrderByOrderIndexAsc(setTheStage))
            .thenReturn(List.of(step1)); // Only 1 step

        // Act
        retroSessionService.advanceToNextStep(sessionId);

        // Assert
        verify(sessionRepository).save(argThat(s ->
            s.getPhase() == RetroPhase.GATHER_DATA &&
            s.getCurrentStepIndex() == 0
        ));
    }

    @Test
    void hasNextStepInCurrentStage_WithMoreSteps_ReturnsTrue() {
        // Arrange
        UUID sessionId = UUID.randomUUID();
        RetroSession session = new RetroSession();
        session.setId(sessionId);
        session.setPhase(RetroPhase.SET_THE_STAGE);
        session.setCurrentStepIndex(0);

        RetroTemplate template = new RetroTemplate();
        RetroStage setTheStage = new RetroStage();
        setTheStage.setId(1L);
        template.setSetTheStage(setTheStage);
        session.setTemplate(template);

        RetroStep step1 = new RetroStep();
        RetroStep step2 = new RetroStep();

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(stepRepository.findByRetroStageOrderByOrderIndexAsc(setTheStage))
            .thenReturn(List.of(step1, step2));

        // Act
        boolean result = retroSessionService.hasNextStepInCurrentStage(sessionId);

        // Assert
        assertTrue(result);
    }

    @Test
    void hasNextStepInCurrentStage_AtLastStep_ReturnsFalse() {
        // Arrange
        UUID sessionId = UUID.randomUUID();
        RetroSession session = new RetroSession();
        session.setId(sessionId);
        session.setPhase(RetroPhase.SET_THE_STAGE);
        session.setCurrentStepIndex(1); // At last step (index 1 of 2 steps)

        RetroTemplate template = new RetroTemplate();
        RetroStage setTheStage = new RetroStage();
        setTheStage.setId(1L);
        template.setSetTheStage(setTheStage);
        session.setTemplate(template);

        RetroStep step1 = new RetroStep();
        RetroStep step2 = new RetroStep();

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(stepRepository.findByRetroStageOrderByOrderIndexAsc(setTheStage))
            .thenReturn(List.of(step1, step2));

        // Act
        boolean result = retroSessionService.hasNextStepInCurrentStage(sessionId);

        // Assert
        assertFalse(result);
    }
}
