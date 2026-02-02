package direct.reflect.facilitator.facilitation.dto;

public record TimerStateDto(
    long remainingSeconds,  // seconds left (0 if expired)
    boolean isPaused,       // true if currently paused
    String state            // "green", "yellow", "red", "expired"
) {}
