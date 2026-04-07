package direct.reflect.facilitator.facilitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import com.redis.testcontainers.RedisContainer;
import direct.reflect.facilitator.config.TestRedisConfig;
import direct.reflect.facilitator.facilitation.actions.ActionItem;
import direct.reflect.facilitator.facilitation.actions.ActionItemRepository;
import direct.reflect.facilitator.facilitation.actions.ActionItemStatus;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@ActiveProfiles({"import", "test"})
@Import(TestRedisConfig.class)
class ActionItemDataModelTest {

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

    @Autowired
    private RetroSessionRepository sessionRepository;

    @Autowired
    private EntityManager entityManager;

    @AfterEach
    void cleanUp() {
        actionItemRepository.deleteAll();
        sessionRepository.deleteAll();
    }

    @Test
    void saveAndFlush_persistsSessionScopedActionItemDefaultsAndStatusChanges() {
        RetroSession retroSession = saveSession("Action Retro");

        ActionItem actionItem = buildActionItem(
                retroSession,
                "Daily sync with design team",
                "Alice",
                LocalDate.of(2026, 5, 1),
                item -> item.setSuccessCriteria("Attendance logged five days per week"));

        actionItemRepository.saveAndFlush(actionItem);
        entityManager.clear();

        ActionItem persistedActionItem = actionItemRepository.findById(actionItem.getId()).orElseThrow();
        LocalDateTime originalUpdatedAt = persistedActionItem.getUpdatedAt();

        assertThat(persistedActionItem.getId()).isNotNull();
        assertThat(persistedActionItem.getRetroSession().getId()).isEqualTo(retroSession.getId());
        assertThat(persistedActionItem.getWhat()).isEqualTo("Daily sync with design team");
        assertThat(persistedActionItem.getWho()).isEqualTo("Alice");
        assertThat(persistedActionItem.getDueDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(persistedActionItem.getSuccessCriteria()).isEqualTo("Attendance logged five days per week");
        assertThat(persistedActionItem.getEscalated()).isFalse();
        assertThat(persistedActionItem.getStatus()).isEqualTo(ActionItemStatus.OPEN);
        assertThat(persistedActionItem.getCreatedByParticipantId()).isNull();
        assertThat(persistedActionItem.getCreatedAt()).isNotNull();
        assertThat(persistedActionItem.getUpdatedAt()).isNotNull();

        persistedActionItem.setStatus(ActionItemStatus.DONE);
        actionItemRepository.saveAndFlush(persistedActionItem);
        entityManager.clear();

        ActionItem updatedActionItem = actionItemRepository.findById(actionItem.getId()).orElseThrow();

        assertThat(updatedActionItem.getStatus()).isEqualTo(ActionItemStatus.DONE);
        assertThat(updatedActionItem.getUpdatedAt()).isAfterOrEqualTo(originalUpdatedAt);
    }

    @Test
    void saveAndFlush_rejectsMissingRequiredFields() {
        RetroSession retroSession = saveSession("Validation Retro");

        assertInvalid(buildActionItem(retroSession, "Daily sync", "Alice", LocalDate.of(2026, 5, 1), item -> item.setWhat(null)));
        assertInvalid(buildActionItem(retroSession, "Daily sync", "Alice", LocalDate.of(2026, 5, 1), item -> item.setWho(null)));
        assertInvalid(buildActionItem(retroSession, "Daily sync", "Alice", LocalDate.of(2026, 5, 1), item -> item.setDueDate(null)));
        assertInvalid(buildActionItem(retroSession, "Daily sync", "Alice", LocalDate.of(2026, 5, 1), item -> item.setRetroSession(null)));
    }

    @Test
    void repositoryQueries_scopeActionItemsBySessionAndStatusInCreationOrder() {
        RetroSession sessionA = saveSession("Session A");
        RetroSession sessionB = saveSession("Session B");

        ActionItem sessionAOpen = actionItemRepository.saveAndFlush(
                buildActionItem(sessionA, "Daily sync", "Alice", LocalDate.of(2026, 5, 1), item -> {}));
        ActionItem sessionADone = actionItemRepository.saveAndFlush(buildActionItem(
                sessionA,
                "Review incidents",
                "Bob",
                LocalDate.of(2026, 5, 8),
                item -> item.setStatus(ActionItemStatus.DONE)));
        actionItemRepository.saveAndFlush(
                buildActionItem(sessionB, "Trim backlog", "Carol", LocalDate.of(2026, 5, 15), item -> {}));

        entityManager.clear();

        List<ActionItem> sessionAItems = actionItemRepository.findByRetroSessionId(sessionA.getId());
        List<ActionItem> sessionAOpenItems = actionItemRepository.findByRetroSessionIdAndStatus(sessionA.getId(), ActionItemStatus.OPEN);

        assertThat(sessionAItems)
                .extracting(ActionItem::getWhat, ActionItem::getStatus, item -> item.getRetroSession().getId())
                .containsExactly(
                        tuple("Daily sync", ActionItemStatus.OPEN, sessionA.getId()),
                        tuple("Review incidents", ActionItemStatus.DONE, sessionA.getId()));

        assertThat(sessionAOpenItems)
                .singleElement()
                .satisfies(actionItem -> {
                    assertThat(actionItem.getId()).isEqualTo(sessionAOpen.getId());
                    assertThat(actionItem.getWhat()).isEqualTo("Daily sync");
                    assertThat(actionItem.getStatus()).isEqualTo(ActionItemStatus.OPEN);
                    assertThat(actionItem.getRetroSession().getId()).isEqualTo(sessionA.getId());
                });
    }

    private RetroSession saveSession(String name) {
        RetroSession retroSession = new RetroSession();
        retroSession.setName(name);
        return sessionRepository.saveAndFlush(retroSession);
    }

    private ActionItem buildActionItem(
            RetroSession retroSession,
            String what,
            String who,
            LocalDate dueDate,
            Consumer<ActionItem> customizer) {
        ActionItem actionItem = new ActionItem();
        actionItem.setRetroSession(retroSession);
        actionItem.setWhat(what);
        actionItem.setWho(who);
        actionItem.setDueDate(dueDate);
        customizer.accept(actionItem);
        return actionItem;
    }

    private void assertInvalid(ActionItem actionItem) {
        assertThatThrownBy(() -> actionItemRepository.saveAndFlush(actionItem))
                .isInstanceOfAny(ConstraintViolationException.class, DataIntegrityViolationException.class);
    }
}
