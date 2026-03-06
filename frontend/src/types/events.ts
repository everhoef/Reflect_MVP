/**
 * SSE event types for Facilitator retrospective sessions.
 *
 * Maps 1:1 to `RetroEvent.EventType` Java enum.
 * Java values are UPPER_SNAKE_CASE; SSE wire format uses lowercase_snake_case
 * (e.g., NOTE_ADDED → event name "note_added").
 */
export enum EventType {
  PARTICIPANT_JOINED = "participant_joined",
  PARTICIPANT_LEFT = "participant_left",
  SESSION_STARTED = "session_started",
  STEP_ADVANCED = "step_advanced",
  RETRO_CREATED = "retro_created",
  PHASE_STARTED = "phase_started",
  NOTE_ADDED = "note_added",
  NOTE_UPDATED = "note_updated",
  NOTE_DELETED = "note_deleted",
  VOTE_ADDED = "vote_added",
  VOTE_REMOVED = "vote_removed",
  GROUP_CREATED = "group_created",
  GROUP_UPDATED = "group_updated",
  GROUP_DELETED = "group_deleted",
  ACTION_CREATED = "action_created",
  ACTION_UPDATED = "action_updated",
  ACTION_DELETED = "action_deleted",
  TIMER_STARTED = "timer_started",
  TIMER_PAUSED = "timer_paused",
  TIMER_FINISHED = "timer_finished",
}

/**
 * ResponseData payload for NOTE events.
 *
 * Maps to `RetroEvent.ResponseData` Java record.
 * Fields match the record components:
 *   responseId, stepId, participantId, participantName,
 *   displaySummary, isVisible, submittedAt
 */
export interface ResponseData {
  responseId: string;
  stepId: number;
  participantId: string;
  participantName: string;
  displaySummary: string;
  isVisible: boolean;
  submittedAt: string; // ISO 8601 instant string
}

/**
 * Discriminated union for NOTE_UPDATED payload.
 *
 * NOTE_UPDATED can be sent in two ways:
 *
 * 1. Single note privacy change (`responsePrivacyChanged()`):
 *    payload is `ResponseData` — identified by presence of `responseId`
 *
 * 2. Batch reveal for a whole step (`responsesRevealed()`):
 *    payload is a `Long` (stepId) — arrives as a plain number on the wire
 *
 * Use `isResponseData()` type guard to distinguish the two.
 */
export type NoteUpdatedPayload = ResponseData | number;

/**
 * Type guard: checks if a NOTE_UPDATED payload is a full ResponseData
 * (single note update) vs. a plain number (batch stepId reveal).
 */
export function isResponseData(payload: NoteUpdatedPayload): payload is ResponseData {
  return typeof payload === "object" && payload !== null && "responseId" in payload;
}

/**
 * Generic SSE event envelope.
 *
 * Maps to the `RetroEvent<T>` Java record.
 * The `event` field on the wire is the lowercase EventType value.
 */
export interface RetroSseEvent<TPayload = unknown> {
  correlationId: string;
  retroId: string;
  type: EventType;
  sourceId: string;
  timestamp: string; // ISO 8601 instant string
  payload: TPayload;
}

/**
 * Typed event variants for all 20 event types.
 * Use these when you need precise payload typing per event.
 */
export type ParticipantJoinedEvent = RetroSseEvent<string>; // displayName
export type ParticipantLeftEvent = RetroSseEvent<string>; // displayName
export type SessionStartedEvent = RetroSseEvent<null>;
export type StepAdvancedEvent = RetroSseEvent<null>;
export type RetroCreatedEvent = RetroSseEvent<null>;
export type PhaseStartedEvent = RetroSseEvent<string>; // phaseName
export type NoteAddedEvent = RetroSseEvent<ResponseData>;
export type NoteUpdatedEvent = RetroSseEvent<NoteUpdatedPayload>;
export type NoteDeletedEvent = RetroSseEvent<ResponseData>;
export type VoteAddedEvent = RetroSseEvent<ResponseData>;
export type VoteRemovedEvent = RetroSseEvent<ResponseData>;
export type GroupCreatedEvent = RetroSseEvent<unknown>; // future
export type GroupUpdatedEvent = RetroSseEvent<unknown>; // future
export type GroupDeletedEvent = RetroSseEvent<unknown>; // future
export type ActionCreatedEvent = RetroSseEvent<unknown>; // future
export type ActionUpdatedEvent = RetroSseEvent<unknown>; // future
export type ActionDeletedEvent = RetroSseEvent<unknown>; // future
export type TimerStartedEvent = RetroSseEvent<null>;
export type TimerPausedEvent = RetroSseEvent<null>;
export type TimerFinishedEvent = RetroSseEvent<null>;
