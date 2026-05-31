export interface AssistantMessage {
  retroId: string
  stepId: number
  stepTitle: string
  publicText: string
}

export interface AssistantState {
  /** The message for the currently active step. */
  current: AssistantMessage | null
  /**
   * Up to HISTORY_MAX_SIZE previous messages, newest-first.
   * Non-facilitators see the same list but with no coaching note.
   */
  history: AssistantMessage[]
  /**
   * Facilitator-private coaching guidance for the current step.
   * Always null for non-facilitators.
   */
  facilitatorCoachingNote: string | null
}

export const HISTORY_MAX_SIZE = 3
