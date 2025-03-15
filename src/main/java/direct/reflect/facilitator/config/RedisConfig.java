package direct.reflect.facilitator.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import direct.reflect.facilitator.messaging.RetroEvent;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.session.data.redis.config.annotation.web.server.EnableRedisWebSession;

@Configuration(proxyBeanMethods = false)
@EnableRedisWebSession
public class RedisConfig {

    /**
     * -----------------------------------------------------
     * Common Redis Configuration
     * -----------------------------------------------------
     */
    
    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory();
    }
    
    /**
     * -----------------------------------------------------
     * Pub/Sub Event Configuration
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
     * Creates a customized serializer for RetroEvent objects
     */
    @Bean
    @SuppressWarnings("rawtypes") // Using raw RetroEvent type for serialization
    public Jackson2JsonRedisSerializer retroEventSerializer(ObjectMapper objectMapper) {
        // Create a serializer for the raw RetroEvent type
        Jackson2JsonRedisSerializer<RetroEvent> serializer = 
            new Jackson2JsonRedisSerializer<>(RetroEvent.class);
        serializer.setObjectMapper(objectMapper);
        return serializer;
    }
    
    @Bean
    @SuppressWarnings({"unchecked", "rawtypes"}) // Necessary for the type casting
    public ReactiveRedisTemplate<String, RetroEvent<?>> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory,
            Jackson2JsonRedisSerializer retroEventSerializer) {
        
        // Create the RedisSerializationContext with simple serializers
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        
        RedisSerializationContext<String, RetroEvent<?>> context = 
            RedisSerializationContext.<String, RetroEvent<?>>newSerializationContext()
                .key(keySerializer)
                .value(retroEventSerializer) 
                .hashKey(keySerializer)
                .hashValue(retroEventSerializer)
                .build();
        
        return new ReactiveRedisTemplate<>(connectionFactory, context);
    }
    
    /**
     * -----------------------------------------------------
     * Spring Session Configuration
     * -----------------------------------------------------
     * 
     * The @EnableRedisWebSession annotation at the class level handles
     * most of the Spring Session with Redis configuration.
     */
}