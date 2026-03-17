package direct.reflect.facilitator.config;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.FixedBackOff;

import java.util.concurrent.CompletableFuture;

import direct.reflect.facilitator.common.config.RedisConfig;

@Configuration(proxyBeanMethods = false)
public class TestRedisConfig {

    /**
     * Replaces the production RedisMessageListenerContainer with a subclass that
     * prevents StackOverflowError caused by a Spring Data Redis 4.0.0 bug.
     *
     * Root cause: RedisMessageListenerContainer.handleSubscriptionException() wraps the
     * passed BackOffExecution in new RecoveryBackoffExecution(backOffExecution) on every
     * reconnect attempt. After N reconnects (we observed ~2139 in a single test run),
     * the delegation chain is N levels deep. When Spring Test 7.0.1 pauses the shared
     * application context between test classes and then restarts it, start() is called on
     * the container, which calls nextBackOff() on this N-deep chain → StackOverflowError.
     *
     * Fix: override handleSubscriptionException to always pass a FRESH BackOffExecution
     * (capturedBackOff.start()) to super, discarding the accumulated nested chain.
     *
     * The message listener (EventService.onPubSubMessage via MessageListenerAdapter) is
     * wired here explicitly, matching the production RedisConfig bean.
     */
    @Bean
    @Primary
    public RedisMessageListenerContainer redisMessageListenerContainer(
            LettuceConnectionFactory connectionFactory,
            MessageListenerAdapter messageListener) {

        NonNestingRedisMessageListenerContainer container =
                new NonNestingRedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(messageListener, new PatternTopic(RedisConfig.ALL_RETROS_PATTERN));
        // Use 200ms retry interval, unlimited attempts — Redis (Testcontainers) is always
        // eventually available. A capped count would exhaust during context restart because
        // the container's attempt counter is not reset on restart.
        container.setRecoveryBackoff(new FixedBackOff(200, FixedBackOff.UNLIMITED_ATTEMPTS));
        // Allow up to 15s for subscription registration (default 2s is too short for CI).
        container.setMaxSubscriptionRegistrationWaitingTime(15000);
        return container;
    }

    /**
     * Keeps the LettuceConnectionFactory configuration from the original BeanPostProcessor.
     * setShareNativeConnection(false) ensures each Lettuce connection is independent,
     * preventing test isolation issues with shared native connections.
     */
    @Bean
    public static BeanPostProcessor lettuceTestConfigurer() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof LettuceConnectionFactory factory) {
                    factory.setShareNativeConnection(false);
                }
                return bean;
            }
        };
    }

    /**
     * A RedisMessageListenerContainer subclass that prevents BackOffExecution nesting.
     *
     * Spring Data Redis 4.0.0 bug: handleSubscriptionException wraps the passed
     * backOffExecution in new RecoveryBackoffExecution(backOffExecution) on every
     * reconnect. After N reconnects, calling nextBackOff() recurses N levels deep →
     * StackOverflowError when the Spring Test context restarts between test classes.
     *
     * Fix: always pass capturedBackOff.start() (a fresh, 1-level execution) to super,
     * discarding the accumulated chain.
     */
    static class NonNestingRedisMessageListenerContainer
            extends RedisMessageListenerContainer {

        private volatile BackOff capturedBackOff =
                new FixedBackOff(200, FixedBackOff.UNLIMITED_ATTEMPTS);

        @Override
        public void setRecoveryBackoff(BackOff recoveryInterval) {
            super.setRecoveryBackoff(recoveryInterval);
            this.capturedBackOff = recoveryInterval;
        }

        @Override
        protected void handleSubscriptionException(CompletableFuture<Void> future,
                BackOffExecution backOffExecution, Throwable cause) {
            // Discard the accumulated nested backOffExecution chain.
            // Always pass a fresh execution from capturedBackOff to prevent
            // the N-deep RecoveryBackoffExecution chain that causes StackOverflowError.
            super.handleSubscriptionException(future, capturedBackOff.start(), cause);
        }
    }
}
