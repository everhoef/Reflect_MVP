package direct.reflect.facilitator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.redis.testcontainers.RedisContainer;

import lombok.extern.slf4j.Slf4j;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "spring.data.redis.repositories.enabled=false"
})
@Slf4j
class FacilitatorApplicationTests {

    @Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:17-alpine")
			.withDatabaseName("postgres")
			.withUsername("postgres")
			.withPassword("postgres");

    @Container
	@ServiceConnection
	static RedisContainer redisContainer = new RedisContainer("redis:alpine");

    @Test
    void contextLoads() {
        log.info("FacilitatorApplicationTests.contextLoads() executed.");
        log.info("Context loaded. If containers are running, Spring Boot has connected to them via @ServiceConnection.");
    }
}
