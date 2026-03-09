import { create } from "zustand";

export interface TimerState {
  timerActive: boolean;
  timerStartedAt: number | null;
  timerDurationSeconds: number | null;
}

export interface StepState {
  currentStepId: number | null;
  currentPhase: string | null;
}

export interface RetroStoreActions {
  setTimerState: (state: TimerState) => void;
  setTimerActive: (active: boolean) => void;
  clearTimer: () => void;
  setCurrentStep: (stepId: number | null) => void;
  setCurrentPhase: (phase: string) => void;
}

export type RetroStoreState = TimerState & StepState & RetroStoreActions;

export const useRetroStateStore = create<RetroStoreState>()((set) => ({
  timerActive: false,
  timerStartedAt: null,
  timerDurationSeconds: null,
  currentStepId: null,
  currentPhase: null,

  setTimerState: (timerState: TimerState) =>
    set({
      timerActive: timerState.timerActive,
      timerStartedAt: timerState.timerStartedAt,
      timerDurationSeconds: timerState.timerDurationSeconds,
    }),

  setTimerActive: (active: boolean) =>
    set({ timerActive: active }),

  clearTimer: () =>
    set({
      timerActive: false,
      timerStartedAt: null,
      timerDurationSeconds: null,
    }),

  setCurrentStep: (stepId: number | null) =>
    set({ currentStepId: stepId }),

  setCurrentPhase: (phase: string) =>
    set({ currentPhase: phase }),
}));
