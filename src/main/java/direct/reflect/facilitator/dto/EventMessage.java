package direct.reflect.facilitator.dto;

public class EventMessage<T> {
  private EventType type;
  private T payload;

  public EventMessage(EventType type, T payload) {
    this.type = type;
    this.payload = payload;
  }

  public EventType getType() {
    return type;
  }

  public T getPayload() {
    return payload;
  }
}
