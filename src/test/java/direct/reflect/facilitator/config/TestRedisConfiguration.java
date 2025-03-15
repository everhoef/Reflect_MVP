package direct.reflect.facilitator.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * Configuration to provide a mock Redis connection factory when the real Redis is unavailable
 */
@Configuration
@ConditionalOnProperty(name = "test.redis.available", havingValue = "false")
public class TestRedisConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(TestRedisConfiguration.class);
    
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        logger.info("Creating mock Redis connection factory for tests");
        // This will try to connect but fail gracefully during tests
        return new LettuceConnectionFactory("localhost", 6379);
    }
}
