package direct.reflect.facilitator.messaging;

import org.springframework.integration.annotation.Gateway;
import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.messaging.Message;

@MessagingGateway
public interface RetroEventGateway {
    @Gateway(requestChannel = "retroEvents.input")
    void publish(Message<RetroEvent<?>> event);
}
