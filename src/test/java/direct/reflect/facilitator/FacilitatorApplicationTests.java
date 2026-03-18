package direct.reflect.facilitator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.redis.testcontainers.RedisContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import lombok.extern.slf4j.Slf4j;

@SpringBootTest
@Testcontainers
@org.springframework.context.annotation.Import(direct.reflect.facilitator.config.TestRedisConfig.class)
@Slf4j
class FacilitatorApplicationTests {
    
    @Autowired(required = false)
    private ClientRegistrationRepository clientRegistrationRepository;

    @Container
    @ServiceConnection
    @SuppressWarnings("resource") // Testcontainers manages the lifecycle
    static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("postgres")
            .withUsername("postgres")
            .withPassword("postgres");

    @Container
    @ServiceConnection
    @SuppressWarnings("resource") // Testcontainers manages the lifecycle
    static RedisContainer redisContainer = new RedisContainer("redis:alpine")
            .withExposedPorts(6379);

    @Test
    void contextLoads() {
        log.info("FacilitatorApplicationTests.contextLoads() executed.");
        log.info("Context loaded. If containers are running, Spring Boot has connected to them via @ServiceConnection.");
        log.info("ClientRegistrationRepository present: {}", clientRegistrationRepository != null);
        if (clientRegistrationRepository != null) {
            log.info("ClientRegistrationRepository type: {}", clientRegistrationRepository.getClass().getSimpleName());
        }
    }
}
