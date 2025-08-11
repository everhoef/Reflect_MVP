package direct.reflect.facilitator.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import direct.reflect.facilitator.eventing.RetroEvent;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

@Configuration(proxyBeanMethods = false)
@EnableRedisHttpSession
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
     * Creates a blocking Redis template for handling generic objects.
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(LettuceConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Configure object mapper for serializing objects
        ObjectMapper mapper = objectMapper.copy()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Create serializers
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        Jackson2JsonRedisSerializer<Object> valueSerializer = new Jackson2JsonRedisSerializer<>(mapper, Object.class);
        
        // Set serializers
        template.setKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashKeySerializer(keySerializer);
        template.setHashValueSerializer(valueSerializer);
        
        template.afterPropertiesSet();
        return template;
    }
    
    /**
     * Creates a reactive Redis template for handling RetroEvent objects.
     */
    @Bean
    public ReactiveRedisTemplate<String, RetroEvent<?>> reactiveRetroEventRedisTemplate(
            ReactiveRedisConnectionFactory factory, ObjectMapper objectMapper) {
        
        // Configure object mapper for serializing RetroEvents
        ObjectMapper retroEventMapper = objectMapper.copy()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Create serializer for RetroEvent objects - use raw type to avoid type inference error
        @SuppressWarnings("rawtypes")
        Jackson2JsonRedisSerializer valueSerializer = 
                new Jackson2JsonRedisSerializer(retroEventMapper, RetroEvent.class);
        
        // Create string serializer for keys
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        
        // Configure serialization context
        @SuppressWarnings({"unchecked", "rawtypes"})
        RedisSerializationContext<String, RetroEvent<?>> serializationContext =
                RedisSerializationContext.<String, RetroEvent<?>>newSerializationContext()
                .key(keySerializer)
                .value(valueSerializer)
                .hashKey(keySerializer)
                .hashValue(valueSerializer)
                .build();
        
        // Create the reactive template
        return new ReactiveRedisTemplate<>(factory, serializationContext);
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