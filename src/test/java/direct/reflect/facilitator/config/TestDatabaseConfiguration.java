package direct.reflect.facilitator.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared database configuration for all integration tests.
 * This class manages database containers and provides their connection properties.
 */
@TestConfiguration
public class TestDatabaseConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(TestDatabaseConfiguration.class);
    
    /**
     * Static PostgreSQL container instance shared across all tests
     */
    @Container
    public static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("postgres")
            .withUsername("postgres")
            .withPassword("postgres")
            .withReuse(true);
    
    /**
     * Static Redis container instance shared across all tests
     */
    @Container
    public static final GenericContainer<?> redis = new GenericContainer<>("redis:alpine")
            .withExposedPorts(6379)
            .withReuse(true)
            .withLogConsumer(new Slf4jLogConsumer(logger));
    
    /**
     * Start containers once on class loading
     */
    static {
        try {
            logger.info("Starting PostgreSQL container...");
            postgres.start();
            logger.info("PostgreSQL running at: {}", postgres.getJdbcUrl());
        } catch (Exception e) {
            logger.error("Failed to start PostgreSQL container", e);
        }
        
        try {
            logger.info("Starting Redis container...");
            redis.start();
            logger.info("Redis running at {}:{}", redis.getHost(), redis.getMappedPort(6379));
        } catch (Exception e) {
            logger.error("Failed to start Redis container", e);
        }
    }
    
    /**
     * Register container properties to be used by Spring Boot
     */
    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        if (postgres.isRunning()) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
            registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        }
        
        if (redis.isRunning()) {
            registry.add("spring.data.redis.host", redis::getHost);
            registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379).toString());
        } else {
            registry.add("spring.session.store-type", () -> "none");
        }
    }
    
    @Bean
    public PostgreSQLContainer<?> postgresContainer() {
        return postgres;
    }
    
    @Bean
    public GenericContainer<?> redisContainer() {
        return redis;
    }
}
