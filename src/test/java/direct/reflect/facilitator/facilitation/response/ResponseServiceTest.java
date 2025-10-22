package direct.reflect.facilitator.facilitation.response;

import direct.reflect.facilitator.facilitation.RetroSession;
import direct.reflect.facilitator.facilitation.Participant;
import direct.reflect.facilitator.facilitation.ParticipantRole;
import direct.reflect.facilitator.configurator.RetroStep;
import direct.reflect.facilitator.configurator.RetroStepRepository;
import direct.reflect.facilitator.configurator.StepType;
import direct.reflect.facilitator.configurator.DataPattern;
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
@DisplayName("ResponseService Tests")
class ResponseServiceTest {

    @Mock private ParticipantResponseRepository participantResponseRepository;
    @Mock private RetroStepRepository retroStepRepository;
    @Mock private EventService eventService;
    @InjectMocks private ResponseService service;

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
            CategoricalResponse savedResponse = createSavedCategoricalResponse(bob, "Our standup meetings are too long", "Mad");
            when(participantResponseRepository.save(any(CategoricalResponse.class))).thenReturn(savedResponse);

            // When: Bob submits his response
            CategoricalResponse result = service.submitCategoricalResponse(
                session, 1L, bob, "Mad", "Our standup meetings are too long"
            );

            // Then: Response should be saved as private and event published
            assertThat(result).isNotNull();
            assertThat(result.getIsVisible()).isFalse(); // Private by default
            assertThat(result.getCategory()).isEqualTo("Mad");
            assertThat(result.getContent()).isEqualTo("Our standup meetings are too long");

            // And: Other participants should be notified via SSE
            verify(eventService).publish(any(RetroEvent.class));
        }

        @Test
        @DisplayName("When Alice (facilitator) reveals all responses, everyone should see Bob's Mad response")
        void facilitator_reveals_all_mad_sad_glad_responses() {
            // Given: Bob has submitted a private Mad response
            CategoricalResponse bobsMadResponse = createSavedCategoricalResponse(bob, "Our standup meetings are too long", "Mad");
            bobsMadResponse.setIsVisible(false);

            CategoricalResponse alicesSadResponse = createSavedCategoricalResponse(alice, "We lost a team member", "Sad");
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
            verify(eventService).publish(any(RetroEvent.class));
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
            RatingResponse existingRating = createSavedRatingResponse(bob, 5, "It was okay");

            when(retroStepRepository.findById(2L)).thenReturn(Optional.of(happinessStep));
            when(participantResponseRepository.findBySessionAndRetroStepAndParticipant(session, happinessStep, bob))
                .thenReturn(Optional.of(existingRating));

            // Update the existing response
            existingRating.setRating(7);
            existingRating.setComment("Better than I initially thought");
            when(participantResponseRepository.save(existingRating)).thenReturn(existingRating);

            // When: Bob updates his rating to 7/10
            RatingResponse result = service.submitRatingResponse(
                session, 2L, bob, 7, 1, 10, "Better than I initially thought"
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

            FreeformResponse bobsResponse = createSavedFreeformResponse(bob, "Challenging");
            FreeformResponse alicesResponse = createSavedFreeformResponse(alice, "Productive");

            when(participantResponseRepository.save(any(FreeformResponse.class)))
                .thenReturn(bobsResponse)
                .thenReturn(alicesResponse);

            // When: Both participants submit their one-word responses
            service.submitFreeformResponse(session, 3L, bob, "Challenging", false);
            service.submitFreeformResponse(session, 3L, alice, "Productive", false);

            // Then: Both responses should be saved and events published
            verify(participantResponseRepository, times(2)).save(any(FreeformResponse.class));
            verify(eventService, times(2)).publish(any(RetroEvent.class));
        }
    }

    @Nested
    @DisplayName("Privacy and Visibility Rules")
    class PrivacyRules {

        @Test
        @DisplayName("Participants should only see visible responses")
        void only_visible_responses_are_returned() {
            // Given: Mixed visible and private responses
            CategoricalResponse bobsPrivateResponse = createSavedCategoricalResponse(bob, "Private thought", "Mad");
            bobsPrivateResponse.setIsVisible(false);

            CategoricalResponse alicesPublicResponse = createSavedCategoricalResponse(alice, "Public thought", "Glad");
            alicesPublicResponse.setIsVisible(true);

            CategoricalResponse bobsPublicResponse = createSavedCategoricalResponse(bob, "Another thought", "Sad");
            bobsPublicResponse.setIsVisible(true);

            when(participantResponseRepository.findVisibleBySessionAndRetroStep(session, madSadGladStep))
                .thenReturn(Arrays.asList(alicesPublicResponse, bobsPublicResponse));

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
            CategoricalResponse savedResponse = createSavedCategoricalResponse(bob, "Test response", "Mad");
            when(participantResponseRepository.save(any(CategoricalResponse.class))).thenReturn(savedResponse);
            doThrow(new RuntimeException("Redis is down")).when(eventService).publish(any());

            // When: Bob tries to submit a response
            // Then: It should not fail even though events can't be published
            assertDoesNotThrow(() -> {
                CategoricalResponse result = service.submitCategoricalResponse(
                    session, 1L, bob, "Mad", "Test response"
                );
                assertThat(result).isNotNull();
            });

            // And: Response should still be saved
            verify(participantResponseRepository).save(any(CategoricalResponse.class));
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

    private CategoricalResponse createSavedCategoricalResponse(Participant participant, String content, String category) {
        CategoricalResponse response = new CategoricalResponse();
        response.setId(UUID.randomUUID());
        response.setRetroStep(madSadGladStep);
        response.setParticipant(participant);
        response.setContent(content);
        response.setCategory(category);
        response.setIsVisible(false);
        response.setSubmittedAt(LocalDateTime.now());
        return response;
    }

    private RatingResponse createSavedRatingResponse(Participant participant, Integer rating, String comment) {
        RatingResponse response = new RatingResponse();
        response.setId(UUID.randomUUID());
        response.setRetroStep(madSadGladStep);
        response.setParticipant(participant);
        response.setRating(rating);
        response.setMinRating(1);
        response.setMaxRating(10);
        response.setComment(comment);
        response.setIsVisible(false);
        response.setSubmittedAt(LocalDateTime.now());
        return response;
    }

    private FreeformResponse createSavedFreeformResponse(Participant participant, String content) {
        FreeformResponse response = new FreeformResponse();
        response.setId(UUID.randomUUID());
        response.setRetroStep(madSadGladStep);
        response.setParticipant(participant);
        response.setContent(content);
        response.setIsMultiLine(false);
        response.setIsVisible(false);
        response.setSubmittedAt(LocalDateTime.now());
        return response;
    }
}
