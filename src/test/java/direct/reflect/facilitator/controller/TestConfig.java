package direct.reflect.facilitator.controller;

import direct.reflect.facilitator.service.EventService;
import direct.reflect.facilitator.service.ParticipantService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.messaging.MessageChannel;
import org.mockito.Mockito;

@TestConfiguration
public class TestConfig {
    
    @Bean
    public MessageChannel retroEventsChannel() {
        return new PublishSubscribeChannel();
    }

    @Bean
    public EventService eventService(MessageChannel retroEventsChannel) {
        return new EventService(retroEventsChannel);
    }

    @Bean
    public ParticipantService participantService() {
        return Mockito.mock(ParticipantService.class);
    }
}
