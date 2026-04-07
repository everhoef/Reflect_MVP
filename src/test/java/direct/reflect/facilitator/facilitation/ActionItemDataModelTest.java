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
import java.util.List;
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
    void saveLoadUpdateAndDelete_persistsSmartFieldsDefaultsAndStatusTransition() {
        RetroSession retroSession = saveSession("Action Retro");

        ActionItem actionItem = new ActionItem();
        actionItem.setRetroSession(retroSession);
        actionItem.setWhat("Daily sync with design team");
        actionItem.setWho("Alice");
        actionItem.setDueDate(LocalDate.of(2026, 5, 1));
        actionItem.setSuccessCriteria("Attendance logged five days per week");

        actionItemRepository.saveAndFlush(actionItem);
        entityManager.clear();

        ActionItem loadedActionItem = actionItemRepository.findById(actionItem.getId()).orElseThrow();

        assertThat(loadedActionItem.getId()).isNotNull();
        assertThat(loadedActionItem.getWhat()).isEqualTo("Daily sync with design team");
        assertThat(loadedActionItem.getWho()).isEqualTo("Alice");
        assertThat(loadedActionItem.getDueDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(loadedActionItem.getSuccessCriteria()).isEqualTo("Attendance logged five days per week");
        assertThat(loadedActionItem.getEscalated()).isFalse();
        assertThat(loadedActionItem.getStatus()).isEqualTo(ActionItemStatus.OPEN);
        assertThat(loadedActionItem.getCreatedByParticipantId()).isNull();
        assertThat(loadedActionItem.getRetroSession().getId()).isEqualTo(retroSession.getId());
        assertThat(loadedActionItem.getCreatedAt()).isNotNull();
        assertThat(loadedActionItem.getUpdatedAt()).isNotNull();

        loadedActionItem.setStatus(ActionItemStatus.DONE);
        actionItemRepository.saveAndFlush(loadedActionItem);
        entityManager.clear();

        ActionItem updatedActionItem = actionItemRepository.findById(actionItem.getId()).orElseThrow();

        assertThat(updatedActionItem.getStatus()).isEqualTo(ActionItemStatus.DONE);
        assertThat(updatedActionItem.getUpdatedAt()).isNotNull();

        actionItemRepository.delete(updatedActionItem);
        actionItemRepository.flush();

        assertThat(actionItemRepository.findById(actionItem.getId())).isEmpty();
    }

    @Test
    void saveAndFlush_rejectsMissingRequiredFields() {
        RetroSession retroSession = saveSession("Validation Retro");

        ActionItem missingWhat = buildActionItem(retroSession, "Daily sync", "Alice", LocalDate.of(2026, 5, 1));
        missingWhat.setWhat(null);

        ActionItem missingWho = buildActionItem(retroSession, "Daily sync", "Alice", LocalDate.of(2026, 5, 1));
        missingWho.setWho(null);

        ActionItem missingDueDate = buildActionItem(retroSession, "Daily sync", "Alice", LocalDate.of(2026, 5, 1));
        missingDueDate.setDueDate(null);

        assertInvalid(missingWhat);
        assertInvalid(missingWho);
        assertInvalid(missingDueDate);
    }

    @Test
    void findByRetroSessionIdAndStatus_scopesResultsToSessionAndStatus() {
        RetroSession sessionA = saveSession("Session A");
        RetroSession sessionB = saveSession("Session B");

        ActionItem sessionAOpen = buildActionItem(sessionA, "Daily sync", "Alice", LocalDate.of(2026, 5, 1));
        ActionItem sessionADone = buildActionItem(sessionA, "Review incidents", "Bob", LocalDate.of(2026, 5, 8));
        sessionADone.setStatus(ActionItemStatus.DONE);
        ActionItem sessionBOpen = buildActionItem(sessionB, "Trim backlog", "Carol", LocalDate.of(2026, 5, 15));

        actionItemRepository.saveAll(List.of(sessionAOpen, sessionADone, sessionBOpen));
        actionItemRepository.flush();
        entityManager.clear();

        List<ActionItem> sessionAItems = actionItemRepository.findByRetroSessionId(sessionA.getId());
        List<ActionItem> sessionAOpenItems = actionItemRepository.findByRetroSessionIdAndStatus(sessionA.getId(), ActionItemStatus.OPEN);

        assertThat(sessionAItems)
                .hasSize(2)
                .extracting(ActionItem::getWhat, ActionItem::getStatus)
                .containsExactlyInAnyOrder(
                        tuple("Daily sync", ActionItemStatus.OPEN),
                        tuple("Review incidents", ActionItemStatus.DONE));
        assertThat(sessionAItems)
                .extracting(actionItem -> actionItem.getRetroSession().getId())
                .containsOnly(sessionA.getId());

        assertThat(sessionAOpenItems)
                .singleElement()
                .satisfies(actionItem -> {
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

    private ActionItem buildActionItem(RetroSession retroSession, String what, String who, LocalDate dueDate) {
        ActionItem actionItem = new ActionItem();
        actionItem.setRetroSession(retroSession);
        actionItem.setWhat(what);
        actionItem.setWho(who);
        actionItem.setDueDate(dueDate);
        return actionItem;
    }

    private void assertInvalid(ActionItem actionItem) {
        assertThatThrownBy(() -> actionItemRepository.saveAndFlush(actionItem))
                .isInstanceOfAny(ConstraintViolationException.class, DataIntegrityViolationException.class);
    }
}
