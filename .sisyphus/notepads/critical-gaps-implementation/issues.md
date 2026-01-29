# Issues & Gotchas

## EventType Enum Disambiguation
- **TWO EventType enums exist**:
  - `RetroEvent.EventType` (nested) - USE THIS for timer events
  - `direct.reflect.facilitator.eventing.EventType` (standalone) - legacy, unused
- RetroSessionService has unused import of standalone EventType - LEAVE IT
- Factory methods in RetroEvent.java use nested enum (no import needed)

## Authorization Patterns
- `getParticipantForSession()` THROWS ParticipantNotFoundException (not returns null)
- New API endpoints: use try/catch pattern
- View fragments: use `@PreAuthorize("@participantService.canAccessRetro(#retroId)")`
