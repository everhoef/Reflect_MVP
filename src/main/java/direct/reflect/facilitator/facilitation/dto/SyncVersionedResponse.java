package direct.reflect.facilitator.facilitation.dto;

public record SyncVersionedResponse<T>(
    long syncVersion,
    T data
) {}
