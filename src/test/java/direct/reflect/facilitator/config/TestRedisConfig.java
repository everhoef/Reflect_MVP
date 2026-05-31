package direct.reflect.facilitator.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.FixedBackOff;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import direct.reflect.facilitator.eventing.infrastructure.redis.RedisPubSubConfig;

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
     * wired here explicitly, matching the production RedisPubSubConfig bean.
     */
    @Bean
    @Primary
    public RedisMessageListenerContainer redisMessageListenerContainer(
            LettuceConnectionFactory connectionFactory,
            MessageListenerAdapter messageListener) {

        NonNestingRedisMessageListenerContainer container =
                new NonNestingRedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(messageListener, new PatternTopic(RedisPubSubConfig.ALL_RETROS_PATTERN));
        container.setRecoveryBackoff(new FixedBackOff(200, FixedBackOff.UNLIMITED_ATTEMPTS));
        container.setMaxSubscriptionRegistrationWaitingTime(15000);
        ScheduledThreadPoolExecutor subExecutor = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "retro-redis-sub");
            t.setDaemon(true);
            return t;
        });
        subExecutor.setRemoveOnCancelPolicy(true);
        container.setSubscriptionExecutor(subExecutor);
        return container;
    }

    /**
     * Patches the Spring Session 'springSessionRedisMessageListenerContainer' bean after it is
     * created by @EnableRedisHttpSession, replacing its backoff with a non-nesting safe version.
     *
     * We cannot override the bean via @Bean because RedisIndexedSessionRepository is not
     * directly autowirable by type (Spring Session registers it under the name "sessionRepository"
     * as SessionRepository). Instead, we use a BeanPostProcessor to intercept the already-created
     * container and use reflection to replace its recoveryBackoff field with a NonNestingBackOff
     * wrapper, preventing the StackOverflowError caused by accumulated BackOffExecution nesting.
     */
    @Bean
    public static BeanPostProcessor springSessionContainerPatcher() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if ("springSessionRedisMessageListenerContainer".equals(beanName)
                        && bean instanceof RedisMessageListenerContainer container) {
                    container.setRecoveryBackoff(new FixedBackOff(200, FixedBackOff.UNLIMITED_ATTEMPTS));
                    // MUST be ScheduledExecutorService — potentiallyRecover() checks instanceof
                    // ScheduledExecutorService. If it's not, it falls back to Thread.sleep(5000)
                    // which blocks [main] indefinitely when Redis is unreachable between tests.
                    ScheduledThreadPoolExecutor subExecutor = new ScheduledThreadPoolExecutor(1, r -> {
                        Thread t = new Thread(r, "spring-session-redis-sub");
                        t.setDaemon(true);
                        return t;
                    });
                    subExecutor.setRemoveOnCancelPolicy(true);
                    container.setSubscriptionExecutor(subExecutor);
                }
                return bean;
            }
        };
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
                if (bean instanceof LettuceConnectionFactory factory
                        && !"springSessionRedisConnectionFactory".equals(beanName)) {
                    factory.setShareNativeConnection(false);
                }
                return bean;
            }
        };
    }

    /**
     * Daemon-thread executor for the Spring Session Redis message listener container's task executor.
     *
     * Spring Session's RedisIndexedHttpSessionConfiguration injects this via
     * @Qualifier("springSessionRedisTaskExecutor") @Autowired(required=false).
     * Using daemon threads ensures the JVM can exit even if the container is retrying a
     * dead Redis connection — critical when Spring Test switches between test contexts and
     * the container tries to reconnect to a stopped Testcontainer Redis instance.
     */
    @Bean
    @Qualifier("springSessionRedisTaskExecutor")
    public Executor springSessionRedisTaskExecutor() {
        return Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "spring-session-redis-task");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Daemon-thread ScheduledExecutorService for the Spring Session Redis subscription executor.
     *
     * Spring Session's RedisIndexedHttpSessionConfiguration injects this via
     * @Qualifier("springSessionRedisSubscriptionExecutor") @Autowired(required=false).
     *
     * MUST be a ScheduledExecutorService (not a plain Executor/ThreadPoolExecutor).
     * Spring Data Redis 4.0.0 RedisMessageListenerContainer.potentiallyRecover() uses:
     *   if (subscriptionExecutor instanceof ScheduledExecutorService ses) {
     *       ses.schedule(retryRunnable, interval, MILLISECONDS);  // non-blocking ✅
     *   } else {
     *       Thread.sleep(interval);  // blocks calling thread ❌
     *       retryRunnable.run();
     *   }
     * When the executor is NOT a ScheduledExecutorService, reconnect retries block the main
     * test thread for 5000ms per attempt indefinitely → Surefire kills the JVM.
     * Using ScheduledThreadPoolExecutor makes potentiallyRecover() non-blocking.
     */
    @Bean
    @Qualifier("springSessionRedisSubscriptionExecutor")
    public Executor springSessionRedisSubscriptionExecutor() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "spring-session-redis-sub");
            t.setDaemon(true);
            return t;
        });
        executor.setRemoveOnCancelPolicy(true);
        return executor;
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
            super.handleSubscriptionException(future, capturedBackOff.start(), cause);
        }
    }
}
