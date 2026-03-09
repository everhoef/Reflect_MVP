import { useShallow } from "zustand/shallow";
import {
  useRetroStateStore,
  type TimerState,
  type RetroStoreActions,
} from "@/store/retroStore";

export function useTimerState(): TimerState {
  return useRetroStateStore(
    useShallow((s) => ({
      timerActive: s.timerActive,
      timerStartedAt: s.timerStartedAt,
      timerDurationSeconds: s.timerDurationSeconds,
    }))
  );
}

export function useRetroActions(): RetroStoreActions {
  return useRetroStateStore(
    useShallow((s) => ({
      setTimerState: s.setTimerState,
      setTimerActive: s.setTimerActive,
      clearTimer: s.clearTimer,
      setCurrentStep: s.setCurrentStep,
      setCurrentPhase: s.setCurrentPhase,
    }))
  );
}

export { useRetroStateStore };
