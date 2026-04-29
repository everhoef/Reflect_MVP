/* tslint:disable */
/* eslint-disable */
// Generated using typescript-generator version 3.2.1263 on 2026-04-29 09:20:14.

export interface RetroEvent<T> {
    correlationId: string;
    retroId: string;
    type: EventType;
    sourceId: string;
    timestamp: string;
    payload: T;
}

export interface ResponseData {
    responseId: string;
    stepId: number;
    participantId: string;
    participantName: string;
    displaySummary: string;
    isVisible: boolean;
    submittedAt: string;
}

export type EventType = "PARTICIPANT_JOINED" | "PARTICIPANT_LEFT" | "SESSION_STARTED" | "STEP_ADVANCED" | "RETRO_CREATED" | "PHASE_STARTED" | "NOTE_ADDED" | "NOTE_UPDATED" | "NOTE_DELETED" | "VOTE_ADDED" | "VOTE_REMOVED" | "GROUP_CREATED" | "GROUP_UPDATED" | "GROUP_DELETED" | "ACTION_CREATED" | "ACTION_UPDATED" | "ACTION_DELETED" | "ESCALATION_CREATED" | "ESCALATION_VOTE_UPDATED" | "TIMER_STARTED" | "TIMER_PAUSED" | "TIMER_FINISHED";
