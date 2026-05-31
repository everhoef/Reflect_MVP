package direct.reflect.facilitator.facilitation.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.redis.testcontainers.RedisContainer;
import direct.reflect.facilitator.config.TestRedisConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@ActiveProfiles({"import", "test"})
@Import(TestRedisConfig.class)
class RetroSyncVersionDataModelTest {

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
    private RetroSessionRepository retroSessionRepository;

    @Autowired
    private RetroSyncVersionService retroSyncVersionService;

    @AfterEach
    void cleanUp() {
        retroSessionRepository.deleteAll();
    }

    @Test
    void saveAndFlush_initializesSyncVersionToZero() {
        RetroSession retroSession = retroSessionRepository.saveAndFlush(new RetroSession());

        RetroSession persisted = retroSessionRepository.findById(retroSession.getId()).orElseThrow();

        assertThat(persisted.getSyncVersion()).isEqualTo(0L);
    }

    @Test
    void bumpSyncVersion_persistsMonotonicIncrementOnSession() {
        RetroSession retroSession = retroSessionRepository.saveAndFlush(new RetroSession());

        long firstVersion = retroSyncVersionService.bumpSyncVersion(retroSession.getId());
        long secondVersion = retroSyncVersionService.bumpSyncVersion(retroSession.getId());

        RetroSession persisted = retroSessionRepository.findById(retroSession.getId()).orElseThrow();

        assertThat(firstVersion).isEqualTo(1L);
        assertThat(secondVersion).isEqualTo(2L);
        assertThat(persisted.getSyncVersion()).isEqualTo(2L);
    }
}
