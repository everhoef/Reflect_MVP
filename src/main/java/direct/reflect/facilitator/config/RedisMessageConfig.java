package direct.reflect.facilitator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.integration.redis.inbound.RedisInboundChannelAdapter;
import org.springframework.integration.redis.outbound.RedisPublishingMessageHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

import direct.reflect.facilitator.messaging.RetroEvent;

import static direct.reflect.facilitator.config.IntegrationConfig.EVENTS_CHANNEL;

@Configuration
public class RedisMessageConfig {
    
    private static final String REDIS_TOPIC_PREFIX = "retro:events:";
    
    @Bean
    public RedisInboundChannelAdapter redisInbound(RedisConnectionFactory connectionFactory,
                                                  Jackson2JsonRedisSerializer<RetroEvent> serializer) {
        RedisInboundChannelAdapter adapter = new RedisInboundChannelAdapter(connectionFactory);
        adapter.setTopics(REDIS_TOPIC_PREFIX + "*");
        adapter.setSerializer(serializer);
        adapter.setOutputChannelName(EVENTS_CHANNEL);
        return adapter;
    }

    @Bean
    public RedisPublishingMessageHandler redisPublisher(RedisConnectionFactory connectionFactory,
                                                      Jackson2JsonRedisSerializer<RetroEvent> serializer) {
        RedisPublishingMessageHandler publisher = new RedisPublishingMessageHandler(connectionFactory);
        publisher.setTopicExpression(
            new SpelExpressionParser().parseExpression("headers['retroId']"));
        publisher.setTopic(EVENTS_CHANNEL);
        publisher.setSerializer(serializer);
        return publisher;
    }

    @Bean
    public Jackson2JsonRedisSerializer<RetroEvent> retroEventSerializer(ObjectMapper objectMapper) {
        return new Jackson2JsonRedisSerializer<>(objectMapper, RetroEvent.class);
    }
}
