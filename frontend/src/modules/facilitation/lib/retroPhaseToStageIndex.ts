/**
 * Maps a backend RetroPhase string to a 1-based stage index (1–5).
 * Phases that are not active retrospective stages return 0 (safe fallback).
 */
export const STAGE_LABELS: readonly string[] = [
  "Set the Stage",
  "Gather Data",
  "Generate Insights",
  "Decide Actions",
  "Close Retro",
];

const PHASE_TO_STAGE: Record<string, number> = {
  SET_THE_STAGE: 1,
  GATHER_DATA: 2,
  GENERATE_INSIGHTS: 3,
  DECIDE_ACTIONS: 4,
  CLOSE_RETRO: 5,
};

export function retroPhaseToStageIndex(phase: string | null | undefined): number {
  if (phase == null) return 0;
  return PHASE_TO_STAGE[phase] ?? 0;
}
