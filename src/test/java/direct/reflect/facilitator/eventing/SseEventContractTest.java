package direct.reflect.facilitator.eventing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract tests for SSE event payload serialization.
 *
 * Verifies that each SSE event type produces valid JSON (not Java toString format)
 * and that the serialized payload contains all fields required by the JSON Schema
 * files in shared/schemas/sse/.
 *
 * These tests catch schema drift: if someone changes a RetroEvent payload record,
 * the test fails immediately rather than silently breaking the React frontend.
 */
@DisplayName("SSE Event Contract Tests")
class SseEventContractTest {

    private ObjectMapper objectMapper;
    private UUID retroId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        retroId = UUID.randomUUID();
    }

    @Test
    @DisplayName("PARTICIPANT_JOINED payload is a JSON string (display name)")
    void participantJoined_payloadIsJsonString() throws Exception {
        RetroEvent<String> event = RetroEvent.participantJoined(retroId, "Alice");

        String json = objectMapper.writeValueAsString(event.payload());

        assertThat(json).isNotNull();
        assertThat(json).doesNotContain("RetroEvent[");
        assertThat(json).doesNotContain("RetroEvent.EventType");
        String parsed = objectMapper.readValue(json, String.class);
        assertThat(parsed).isEqualTo("Alice");
    }

    @Test
    @DisplayName("PARTICIPANT_LEFT payload is a JSON string (display name)")
    void participantLeft_payloadIsJsonString() throws Exception {
        RetroEvent<String> event = RetroEvent.participantLeft(retroId, "Bob");

        String json = objectMapper.writeValueAsString(event.payload());

        String parsed = objectMapper.readValue(json, String.class);
        assertThat(parsed).isEqualTo("Bob");
    }

    @Test
    @DisplayName("SESSION_STARTED payload is null (void event)")
    void sessionStarted_payloadIsNull() {
        RetroEvent<Void> event = RetroEvent.sessionStarted(retroId);

        assertThat(event.payload()).isNull();
    }

    @Test
    @DisplayName("STEP_ADVANCED payload is null (void event)")
    void stepAdvanced_payloadIsNull() {
        RetroEvent<Void> event = RetroEvent.stepAdvanced(retroId);

        assertThat(event.payload()).isNull();
    }

    @Test
    @DisplayName("RETRO_CREATED payload is null (void event)")
    void retroCreated_payloadIsNull() {
        RetroEvent<Void> event = RetroEvent.retroCreated(retroId, "facilitator-123");

        assertThat(event.payload()).isNull();
    }

    @Test
    @DisplayName("PHASE_STARTED payload is a JSON string (phase name)")
    void phaseStarted_payloadIsJsonString() throws Exception {
        RetroEvent<String> event = RetroEvent.phaseStarted(retroId, "facilitator-123", "GATHER_DATA");

        String json = objectMapper.writeValueAsString(event.payload());

        String parsed = objectMapper.readValue(json, String.class);
        assertThat(parsed).isEqualTo("GATHER_DATA");
    }

    @Test
    @DisplayName("NOTE_ADDED payload is valid JSON with all ResponseData fields")
    void noteAdded_payloadIsValidResponseDataJson() throws Exception {
        String participantId = UUID.randomUUID().toString();
        RetroEvent.ResponseData responseData = new RetroEvent.ResponseData(
            UUID.randomUUID().toString(),
            1L,
            participantId,
            "Alice",
            "Something went wrong",
            false,
            Instant.now()
        );
        RetroEvent<RetroEvent.ResponseData> event = RetroEvent.responseSubmitted(retroId, participantId, responseData);

        String json = objectMapper.writeValueAsString(event.payload());

        assertThat(json).doesNotContain("ResponseData[");
        assertThat(json).doesNotContain("@");

        JsonNode node = objectMapper.readTree(json);
        assertThat(node.has("responseId")).isTrue();
        assertThat(node.has("stepId")).isTrue();
        assertThat(node.has("participantId")).isTrue();
        assertThat(node.has("participantName")).isTrue();
        assertThat(node.has("displaySummary")).isTrue();
        assertThat(node.has("isVisible")).isTrue();
        assertThat(node.has("submittedAt")).isTrue();

        assertThat(node.get("responseId").isTextual()).isTrue();
        assertThat(node.get("stepId").isNumber()).isTrue();
        assertThat(node.get("participantId").isTextual()).isTrue();
        assertThat(node.get("participantName").isTextual()).isTrue();
        assertThat(node.get("displaySummary").isTextual()).isTrue();
        assertThat(node.get("isVisible").isBoolean()).isTrue();
        assertThat(node.get("submittedAt").isTextual()).isTrue();

        assertThat(node.get("participantName").asText()).isEqualTo("Alice");
        assertThat(node.get("displaySummary").asText()).isEqualTo("Something went wrong");
        assertThat(node.get("isVisible").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("NOTE_UPDATED (privacy changed) payload is valid ResponseData JSON")
    void noteUpdated_privacyChanged_payloadIsResponseDataJson() throws Exception {
        String participantId = UUID.randomUUID().toString();
        RetroEvent.ResponseData responseData = new RetroEvent.ResponseData(
            UUID.randomUUID().toString(),
            2L,
            participantId,
            "Bob",
            "It's okay now",
            true,
            Instant.now()
        );
        RetroEvent<RetroEvent.ResponseData> event = RetroEvent.responsePrivacyChanged(retroId, "facilitator-123", responseData);

        String json = objectMapper.writeValueAsString(event.payload());

        assertThat(json).doesNotContain("ResponseData[");
        JsonNode node = objectMapper.readTree(json);
        assertThat(node.has("responseId")).isTrue();
        assertThat(node.has("stepId")).isTrue();
        assertThat(node.get("isVisible").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("NOTE_UPDATED (batch reveal) payload is a JSON integer (stepId)")
    void noteUpdated_responsesRevealed_payloadIsStepId() throws Exception {
        RetroEvent<Long> event = RetroEvent.responsesRevealed(retroId, "facilitator-123", 42L);

        String json = objectMapper.writeValueAsString(event.payload());

        assertThat(json).doesNotContain("Long");
        Long parsed = objectMapper.readValue(json, Long.class);
        assertThat(parsed).isEqualTo(42L);
    }

    @Test
    @DisplayName("TIMER_PAUSED payload is null (void event)")
    void timerPaused_payloadIsNull() {
        RetroEvent<Void> event = RetroEvent.timerPaused(retroId);

        assertThat(event.payload()).isNull();
    }

    @Test
    @DisplayName("TIMER_STARTED payload is null (void event)")
    void timerStarted_payloadIsNull() {
        RetroEvent<Void> event = RetroEvent.timerStarted(retroId);

        assertThat(event.payload()).isNull();
    }

    @Test
    @DisplayName("Event envelope serializes to valid JSON with all required fields")
    void eventEnvelope_serializesToValidJson() throws Exception {
        String participantId = UUID.randomUUID().toString();
        RetroEvent.ResponseData responseData = new RetroEvent.ResponseData(
            UUID.randomUUID().toString(),
            1L,
            participantId,
            "Charlie",
            "Test note",
            false,
            Instant.now()
        );
        RetroEvent<RetroEvent.ResponseData> event = RetroEvent.responseSubmitted(retroId, participantId, responseData);

        String envelopeJson = objectMapper.writeValueAsString(event);

        assertThat(envelopeJson).doesNotContain("RetroEvent[");

        JsonNode envelope = objectMapper.readTree(envelopeJson);
        assertThat(envelope.has("correlationId")).isTrue();
        assertThat(envelope.has("retroId")).isTrue();
        assertThat(envelope.has("type")).isTrue();
        assertThat(envelope.has("sourceId")).isTrue();
        assertThat(envelope.has("timestamp")).isTrue();
        assertThat(envelope.has("payload")).isTrue();

        assertThat(envelope.get("correlationId").asText()).startsWith("evt-");
        assertThat(envelope.get("type").asText()).isEqualTo("NOTE_ADDED");
        assertThat(envelope.get("payload").has("responseId")).isTrue();
    }
}
