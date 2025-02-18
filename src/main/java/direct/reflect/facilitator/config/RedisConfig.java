package direct.reflect.facilitator.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import direct.reflect.facilitator.messaging.RetroEvent;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.session.data.redis.config.annotation.web.server.EnableRedisWebSession;

@Configuration(proxyBeanMethods = false)
@EnableRedisWebSession 
public class RedisConfig {

    @Bean
    public RedisConnectionFactory connectionFactory() {
        // Creates Redis connection using Lettuce driver
        return new LettuceConnectionFactory();
    }
    
    @Bean
    public RedisTemplate<String, RetroEvent> redisTemplate(
            RedisConnectionFactory connectionFactory, 
            ObjectMapper objectMapper) {
        // Configure Redis template with proper serialization
        RedisTemplate<String, RetroEvent> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Use String serializer for keys
        template.setKeySerializer(new StringRedisSerializer());
        
        // Use Jackson for RetroEvent serialization
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(objectMapper, RetroEvent.class));
        template.afterPropertiesSet();
        
        return template;
    }
}