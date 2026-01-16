package direct.reflect.facilitator.common.config;

import tools.jackson.databind.ObjectMapper;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

import direct.reflect.facilitator.eventing.EventService;

@Configuration(proxyBeanMethods = false)
@EnableRedisHttpSession
public class RedisConfig {

    /**
     * -----------------------------------------------------
     * Common Redis Configuration
     * -----------------------------------------------------
     */


    /**
     * -----------------------------------------------------
     * Pub/Sub Event Configuration (Broadcast to all pods)
     * -----------------------------------------------------
     */

    // Channel prefix for retro events
    public static final String RETRO_CHANNEL_PREFIX = "retro:";

    // Pattern to subscribe to all retro channels
    public static final String ALL_RETROS_PATTERN = RETRO_CHANNEL_PREFIX + "*";
    
    /**
     * Get the channel name for a specific retro
     * @param retroId The ID of the retrospective
     * @return The channel name for the given retro ID
     */
    public static String getChannelForRetro(String retroId) {
        return RETRO_CHANNEL_PREFIX + retroId;
    }
    
    /**
     * Creates a blocking Redis template for handling generic objects.
     *
     * Jackson 3 changes:
     * - JavaTimeModule is now built-in to jackson-databind (no registration needed)
     * - WRITE_DATES_AS_TIMESTAMPS defaults to false (was true in Jackson 2)
     * - Spring Boot's auto-configured ObjectMapper already has proper settings
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(LettuceConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Create serializers using Spring Boot's auto-configured ObjectMapper
        // No customization needed - Jackson 3 has sensible defaults for Java Time types
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        JacksonJsonRedisSerializer<Object> valueSerializer = new JacksonJsonRedisSerializer<>(objectMapper, Object.class);

        // Set serializers
        template.setKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashKeySerializer(keySerializer);
        template.setHashValueSerializer(valueSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * Message listener adapter for Pub/Sub events.
     * Delegates messages to EventService.onPubSubMessage().
     */
    @Bean
    public MessageListenerAdapter messageListener(EventService eventService) {
        return new MessageListenerAdapter(eventService, "onPubSubMessage");
    }

    /**
     * Redis message listener container for Pub/Sub.
     * Subscribes to all retro channels (retro:*)
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            LettuceConnectionFactory connectionFactory,
            MessageListenerAdapter messageListener) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        // Subscribe to all retro channels: retro:*
        container.addMessageListener(messageListener, new PatternTopic(ALL_RETROS_PATTERN));

        return container;
    }

    /**
     * -----------------------------------------------------
     * Spring Session Configuration
     * -----------------------------------------------------
     *
     * The @EnableRedisHttpSession annotation at the class level handles
     * most of the Spring Session with Redis configuration.
     */
}