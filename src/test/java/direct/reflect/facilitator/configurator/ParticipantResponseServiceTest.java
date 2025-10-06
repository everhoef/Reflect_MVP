package direct.reflect.facilitator.configurator;

import direct.reflect.facilitator.facilitation.RetroSession;
import direct.reflect.facilitator.facilitation.Participant;
import direct.reflect.facilitator.facilitation.ParticipantRole;
import direct.reflect.facilitator.eventing.EventService;
import direct.reflect.facilitator.eventing.RetroEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import java.util.Arrays;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("ParticipantResponseService Tests")
class ParticipantResponseServiceTest {

    @Mock private ParticipantResponseRepository participantResponseRepository;
    @Mock private RetroStepRepository retroStepRepository;
    @Mock private EventService eventService;
    @InjectMocks private ParticipantResponseService service;
    
    private RetroSession session;
    private Participant alice;
    private Participant bob;
    private RetroStep madSadGladStep;
    
    @BeforeEach
    void setUp() {
        session = createTestSession("Team Retro Session");
        alice = createTestParticipant("Alice", ParticipantRole.FACILITATOR);
        bob = createTestParticipant("Bob", ParticipantRole.PARTICIPANT);
        madSadGladStep = createTestStep("Mad Sad Glad", StepType.ACTIVITY, DataPattern.CATEGORICAL);
    }
    
    @Nested
    @DisplayName("Scenario: Participants submit Mad-Sad-Glad responses")
    class MadSadGladScenario {
        
        @Test
        @DisplayName("When Bob adds 'Our standup meetings are too long' to Mad category, it should be private by default")
        void participant_submits_mad_response_private_by_default() {
            // Given: Bob wants to submit a Mad response
            when(retroStepRepository.findById(1L)).thenReturn(Optional.of(madSadGladStep));
            ParticipantResponse savedResponse = createSavedResponse(bob, "Our standup meetings are too long", "Mad");
            when(participantResponseRepository.save(any(ParticipantResponse.class))).thenReturn(savedResponse);
            
            // When: Bob submits his response
            ParticipantResponse result = service.submitCategoricalResponse(
                session, 1L, bob, "Mad", "Our standup meetings are too long"
            );
            
            // Then: Response should be saved as private and event published
            assertThat(result).isNotNull();
            assertThat(result.getIsVisible()).isFalse(); // Private by default
            assertThat(result.getCategory()).isEqualTo("Mad");
            assertThat(result.getContent()).isEqualTo("Our standup meetings are too long");
            
            // And: Other participants should be notified via SSE
            ArgumentCaptor<RetroEvent<?>> eventCaptor = ArgumentCaptor.forClass(RetroEvent.class);
            verify(eventService).publish(eventCaptor.capture());
            
            RetroEvent<?> event = eventCaptor.getValue();
            assertThat(event.type()).isEqualTo(RetroEvent.EventType.NOTE_ADDED);
            assertThat(event.sourceId()).isEqualTo(bob.getParticipantId().toString());
            
            RetroEvent.ResponseData data = (RetroEvent.ResponseData) event.payload();
            assertThat(data.participantName()).isEqualTo("Bob");
            assertThat(data.isVisible()).isFalse(); // Still private in the event
        }
        
        @Test
        @DisplayName("When Alice (facilitator) reveals all responses, everyone should see Bob's Mad response")
        void facilitator_reveals_all_mad_sad_glad_responses() {
            // Given: Bob has submitted a private Mad response
            ParticipantResponse bobsMadResponse = createSavedResponse(bob, "Our standup meetings are too long", "Mad");
            bobsMadResponse.setIsVisible(false);
            
            ParticipantResponse alicesSadResponse = createSavedResponse(alice, "We lost a team member", "Sad");
            alicesSadResponse.setIsVisible(false);
            
            when(retroStepRepository.findById(1L)).thenReturn(Optional.of(madSadGladStep));
            when(participantResponseRepository.findBySessionAndRetroStep(session, madSadGladStep))
                .thenReturn(Arrays.asList(bobsMadResponse, alicesSadResponse));
            
            // When: Alice reveals all responses
            service.revealAllResponses(session, 1L);
            
            // Then: All responses should become visible
            assertThat(bobsMadResponse.getIsVisible()).isTrue();
            assertThat(alicesSadResponse.getIsVisible()).isTrue();
            verify(participantResponseRepository).saveAll(Arrays.asList(bobsMadResponse, alicesSadResponse));
            
            // And: Everyone should be notified that responses are now public
            ArgumentCaptor<RetroEvent<?>> eventCaptor = ArgumentCaptor.forClass(RetroEvent.class);
            verify(eventService).publish(eventCaptor.capture());
            
            RetroEvent<?> event = eventCaptor.getValue();
            assertThat(event.type()).isEqualTo(RetroEvent.EventType.NOTE_UPDATED);
            assertThat(event.sourceId()).isEqualTo("facilitator");
            assertThat(event.payload()).isEqualTo(1L); // Step ID
        }
    }
    
    @Nested
    @DisplayName("Scenario: Team rates sprint happiness (1-10 scale)")
    class HappinessRatingScenario {
        
        @Test
        @DisplayName("When Bob rates the sprint as 7/10 with comment, it should update his existing rating")
        void participant_updates_happiness_rating() {
            // Given: Bob previously rated the sprint as 5/10
            RetroStep happinessStep = createTestStep("Rate Sprint Happiness", StepType.ACTIVITY, DataPattern.RATING);
            ParticipantResponse existingRating = createSavedResponse(bob, null, null);
            existingRating.setRating(5);
            existingRating.setComment("It was okay");
            
            when(retroStepRepository.findById(2L)).thenReturn(Optional.of(happinessStep));
            when(participantResponseRepository.findBySessionAndRetroStepAndParticipant(session, happinessStep, bob))
                .thenReturn(Optional.of(existingRating));
            
            // Update the existing response
            existingRating.setRating(7);
            existingRating.setComment("Better than I initially thought");
            when(participantResponseRepository.save(existingRating)).thenReturn(existingRating);
            
            // When: Bob updates his rating to 7/10
            ParticipantResponse result = service.submitRatingResponse(
                session, 2L, bob, 7, "Better than I initially thought"
            );
            
            // Then: His rating should be updated
            assertThat(result.getRating()).isEqualTo(7);
            assertThat(result.getComment()).isEqualTo("Better than I initially thought");
            
            // And: Event should be published for real-time updates
            verify(eventService).publish(any(RetroEvent.class));
        }
    }
    
    @Nested
    @DisplayName("Scenario: One-word retrospective responses")
    class OneWordScenario {
        
        @Test
        @DisplayName("When participants submit one-word responses, each should be a separate response")
        void multiple_participants_submit_one_word_responses() {
            // Given: A freeform step asking for one word
            RetroStep oneWordStep = createTestStep("One Word Summary", StepType.ACTIVITY, DataPattern.FREEFORM);
            when(retroStepRepository.findById(3L)).thenReturn(Optional.of(oneWordStep));
            
            ParticipantResponse bobsResponse = createSavedResponse(bob, "Challenging", null);
            ParticipantResponse alicesResponse = createSavedResponse(alice, "Productive", null);
            
            when(participantResponseRepository.save(any(ParticipantResponse.class)))
                .thenReturn(bobsResponse)
                .thenReturn(alicesResponse);
            
            // When: Both participants submit their one-word responses
            service.submitFreeformResponse(session, 3L, bob, "Challenging");
            service.submitFreeformResponse(session, 3L, alice, "Productive");
            
            // Then: Both responses should be saved and events published
            verify(participantResponseRepository, times(2)).save(any(ParticipantResponse.class));
            verify(eventService, times(2)).publish(any(RetroEvent.class));
        }
    }
    
    @Nested
    @DisplayName("Privacy and Visibility Rules")
    class PrivacyRules {
        
        @Test
        @DisplayName("Participants should only see their own responses until facilitator reveals all")
        void only_visible_responses_are_returned() {
            // Given: Mixed visible and private responses
            ParticipantResponse bobsPrivateResponse = createSavedResponse(bob, "Private thought", "Mad");
            bobsPrivateResponse.setIsVisible(false);
            
            ParticipantResponse alicesPublicResponse = createSavedResponse(alice, "Public thought", "Glad");
            alicesPublicResponse.setIsVisible(true);
            
            ParticipantResponse bobsPublicResponse = createSavedResponse(bob, "Another thought", "Sad");
            bobsPublicResponse.setIsVisible(true);
            
            when(participantResponseRepository.findBySessionAndRetroStep(session, madSadGladStep))
                .thenReturn(Arrays.asList(bobsPrivateResponse, alicesPublicResponse, bobsPublicResponse));
            
            // When: Getting visible responses (what UI should show to other participants)
            List<ParticipantResponse> visibleResponses = service.getVisibleResponsesForStep(session, madSadGladStep);
            
            // Then: Only public responses should be returned
            assertThat(visibleResponses).hasSize(2);
            assertThat(visibleResponses).contains(alicesPublicResponse, bobsPublicResponse);
            assertThat(visibleResponses).doesNotContain(bobsPrivateResponse);
        }
        
        @Test
        @DisplayName("Service should handle event publishing failures gracefully")
        void event_failure_should_not_break_response_submission() {
            // Given: Event service will fail
            when(retroStepRepository.findById(1L)).thenReturn(Optional.of(madSadGladStep));
            ParticipantResponse savedResponse = createSavedResponse(bob, "Test response", "Mad");
            when(participantResponseRepository.save(any(ParticipantResponse.class))).thenReturn(savedResponse);
            doThrow(new RuntimeException("Redis is down")).when(eventService).publish(any());
            
            // When: Bob tries to submit a response
            // Then: It should not fail even though events can't be published
            assertDoesNotThrow(() -> {
                ParticipantResponse result = service.submitCategoricalResponse(
                    session, 1L, bob, "Mad", "Test response"
                );
                assertThat(result).isNotNull();
            });
            
            // And: Response should still be saved
            verify(participantResponseRepository).save(any(ParticipantResponse.class));
        }
    }
    
    // Helper methods to create test data with meaningful names
    private RetroSession createTestSession(String name) {
        RetroSession session = new RetroSession();
        session.setId(UUID.randomUUID());
        session.setName(name);
        return session;
    }
    
    private Participant createTestParticipant(String name, ParticipantRole role) {
        Participant participant = new Participant();
        participant.setParticipantId(UUID.randomUUID());
        participant.setDisplayName(name);
        participant.setRole(role);
        participant.setSession(session);
        return participant;
    }
    
    private RetroStep createTestStep(String title, StepType stepType, DataPattern dataPattern) {
        RetroStep step = new RetroStep();
        step.setId(1L);
        step.setTitle(title);
        step.setStepType(stepType);
        step.setDataPattern(dataPattern);
        return step;
    }
    
    private ParticipantResponse createSavedResponse(Participant participant, String content, String category) {
        ParticipantResponse response = new ParticipantResponse();
        response.setId(UUID.randomUUID().toString());
        response.setRetroStep(madSadGladStep);
        response.setParticipant(participant);
        response.setContent(content);
        response.setCategory(category);
        response.setIsVisible(false);
        response.setSubmittedAt(LocalDateTime.now());
        return response;
    }
}