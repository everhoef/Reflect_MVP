package direct.reflect.facilitator.integration;

import com.redis.testcontainers.RedisContainer;
import direct.reflect.facilitator.facilitation.actionitem.ActionItem;
import direct.reflect.facilitator.facilitation.actionitem.ActionItemRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("import")
@Slf4j
class ActionItemRepositoryTest {

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("postgres")
            .withUsername("postgres")
            .withPassword("postgres");

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static RedisContainer redisContainer = new RedisContainer("redis:alpine")
            .withExposedPorts(6379);

    @Autowired
    private ActionItemRepository actionItemRepository;

    @AfterEach
    void cleanUp() {
        actionItemRepository.deleteAll();
    }

    @Test
    void saveAndFindByRetroSessionId_returnsMatchingItem() {
        UUID sessionId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();

        ActionItem item = ActionItem.builder()
                .what("Improve daily standup efficiency")
                .retroSessionId(sessionId)
                .assignedToParticipantId(participantId)
                .whenDate(LocalDate.of(2026, 3, 1))
                .successCriteria("Standups finish in under 15 minutes")
                .build();

        actionItemRepository.save(item);

        List<ActionItem> results = actionItemRepository.findByRetroSessionId(sessionId);

        assertThat(results).hasSize(1);
        ActionItem saved = results.get(0);
        assertThat(saved.getWhat()).isEqualTo("Improve daily standup efficiency");
        assertThat(saved.getRetroSessionId()).isEqualTo(sessionId);
        assertThat(saved.getAssignedToParticipantId()).isEqualTo(participantId);
        assertThat(saved.getWhenDate()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(saved.getSuccessCriteria()).isEqualTo("Standups finish in under 15 minutes");
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void findByRetroSessionId_returnsAllItemsForSession() {
        UUID sessionId = UUID.randomUUID();

        ActionItem item1 = ActionItem.builder()
                .what("Action one")
                .retroSessionId(sessionId)
                .build();

        ActionItem item2 = ActionItem.builder()
                .what("Action two")
                .retroSessionId(sessionId)
                .build();

        ActionItem otherSession = ActionItem.builder()
                .what("Other session action")
                .retroSessionId(UUID.randomUUID())
                .build();

        actionItemRepository.saveAll(List.of(item1, item2, otherSession));

        List<ActionItem> results = actionItemRepository.findByRetroSessionId(sessionId);

        assertThat(results).hasSize(2);
        assertThat(results).extracting("what")
                .containsExactlyInAnyOrder("Action one", "Action two");
    }

    @Test
    void findByAssignedToParticipantId_returnsCorrectItem() {
        UUID sessionId = UUID.randomUUID();
        UUID participantA = UUID.randomUUID();
        UUID participantB = UUID.randomUUID();

        ActionItem itemA = ActionItem.builder()
                .what("Task for participant A")
                .retroSessionId(sessionId)
                .assignedToParticipantId(participantA)
                .build();

        ActionItem itemB = ActionItem.builder()
                .what("Task for participant B")
                .retroSessionId(sessionId)
                .assignedToParticipantId(participantB)
                .build();

        actionItemRepository.saveAll(List.of(itemA, itemB));

        List<ActionItem> resultsA = actionItemRepository.findByAssignedToParticipantId(participantA);

        assertThat(resultsA).hasSize(1);
        assertThat(resultsA.get(0).getWhat()).isEqualTo("Task for participant A");
    }
}
