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
                    // Use unlimited retries with a 200ms interval.
                    // A fixed cap of attempts (e.g. 50) causes "Subscription attempts exceeded" when
                    // Spring test infrastructure restarts a shared context between test classes —
                    // the container's attempt counter is not reset on restart, so a capped backoff
                    // exhausts immediately. Testcontainers keeps Redis alive for the full test run,
                    // so unlimited retries are safe; the container will recover as long as Redis is up.
                    container.setRecoveryBackoff(new FixedBackOff(200, FixedBackOff.UNLIMITED_ATTEMPTS));
                    // Allow up to 15s for subscription registration (default is 2s, too short for CI).
                    container.setMaxSubscriptionRegistrationWaitingTime(15000);
                }
                return bean;
            }
        };
    }
}
