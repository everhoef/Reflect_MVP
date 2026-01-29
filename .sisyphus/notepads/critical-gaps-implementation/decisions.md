# Architectural Decisions

## Timer Implementation
- **Server-authoritative**: Timer calculation happens on server, not client
- **LocalDateTime**: Using LocalDateTime (not Instant) to match existing fields
- **Pause tracking**: Two fields (pausedAt + accumulated) instead of pause history table
- **Event naming**: Use TIMER_STARTED for resume (no TIMER_RESUMED enum exists)
