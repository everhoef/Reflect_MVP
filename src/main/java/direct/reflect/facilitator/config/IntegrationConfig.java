package direct.reflect.facilitator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.messaging.MessageChannel;

@Configuration
public class IntegrationConfig {
    
    public static final String EVENTS_CHANNEL = "retroEvents";
    
    @Bean
    public MessageChannel eventsChannel() {
        return new PublishSubscribeChannel();
    }

    @Bean
    public IntegrationFlow eventsFlow() {
        return IntegrationFlow.from(EVENTS_CHANNEL)
            .filter(message -> message != null)
            .get();
    }
}
