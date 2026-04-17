package direct.reflect.facilitator.eventing;

public record RetroSseEnvelope<T>(
    long syncVersion,
    T payload
) {}
