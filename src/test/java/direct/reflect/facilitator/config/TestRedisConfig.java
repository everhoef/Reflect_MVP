package direct.reflect.facilitator.config;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.util.backoff.FixedBackOff;

@Configuration(proxyBeanMethods = false)
public class TestRedisConfig {

    @Bean
    public static BeanPostProcessor redisTestBeanConfigurer() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof LettuceConnectionFactory factory) {
                    factory.setShareNativeConnection(false);
                }
                if (bean instanceof RedisMessageListenerContainer container) {
                    // Prevent infinite retry loops during Spring context pause/restart.
                    // Default backoff is unlimited — cap retries so the suite terminates.
                    // 200ms interval × 50 attempts = 10s max retry window (enough for Testcontainers).
                    container.setRecoveryBackoff(new FixedBackOff(200, 50));
                    // Allow up to 15s for subscription registration (default is 2s, too short for CI).
                    container.setMaxSubscriptionRegistrationWaitingTime(15000);
                }
                return bean;
            }
        };
    }
}
