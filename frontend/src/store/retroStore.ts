import { create } from "zustand";

export interface TimerState {
  remainingSeconds: number | null;  // null = no timer active
  isPaused: boolean;
  timerState: string | null;        // "green" | "yellow" | "red" | null
}

export interface StepState {
  currentStepId: number | null;
  currentPhase: string | null;
}

export interface RetroStoreActions {
  setTimerState: (state: TimerState) => void;
  clearTimer: () => void;
  setCurrentStep: (stepId: number | null) => void;
  setCurrentPhase: (phase: string) => void;
}

export type RetroStoreState = TimerState & StepState & RetroStoreActions;

export const useRetroStateStore = create<RetroStoreState>()((set) => ({
  remainingSeconds: null,
  isPaused: false,
  timerState: null,
  currentStepId: null,
  currentPhase: null,

  setTimerState: (state: TimerState) =>
    set({
      remainingSeconds: state.remainingSeconds,
      isPaused: state.isPaused,
      timerState: state.timerState,
    }),

  clearTimer: () =>
    set({
      remainingSeconds: null,
      isPaused: false,
      timerState: null,
    }),

  setCurrentStep: (stepId: number | null) =>
    set({ currentStepId: stepId }),

  setCurrentPhase: (phase: string) =>
    set({ currentPhase: phase }),
}));
