package direct.reflect.facilitator.service;

import lombok.RequiredArgsConstructor;

import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.stereotype.Service;

import direct.reflect.facilitator.messaging.RetroEvent;
import reactor.core.publisher.Flux;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventService {
    private final MessageChannel retroEventsChannel;
    
    public void publish(RetroEvent<?> event) {
        retroEventsChannel.send(MessageBuilder.withPayload(event)
            .setHeader("retroId", event.retroId().toString())
            .build());
    }
    
    public Flux<RetroEvent<?>> subscribeToRetro(UUID retroId) {
        return Flux.create(emitter -> {
            MessageHandler handler = message -> {
                RetroEvent<?> event = (RetroEvent<?>) message.getPayload();
                if (retroId.equals(event.retroId())) {
                    emitter.next(event);
                }
            };
            
            ((SubscribableChannel) retroEventsChannel).subscribe(handler);
            emitter.onDispose(() -> 
                ((SubscribableChannel) retroEventsChannel).unsubscribe(handler));
        });
    }
}
