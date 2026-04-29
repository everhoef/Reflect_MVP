import { useShallow } from "zustand/shallow";
import {
  useRetroStateStore,
  type TimerState,
  type RetroStoreActions,
} from "@/modules/facilitation/store/retroStore";

export function useTimerState(): TimerState {
  return useRetroStateStore(
    useShallow((s) => ({
      remainingSeconds: s.remainingSeconds,
      isPaused: s.isPaused,
      timerState: s.timerState,
    }))
  );
}

export function useRetroActions(): RetroStoreActions {
  return useRetroStateStore(
    useShallow((s) => ({
      setTimerState: s.setTimerState,
      clearTimer: s.clearTimer,
      setCurrentStep: s.setCurrentStep,
      setCurrentPhase: s.setCurrentPhase,
    }))
  );
}

export { useRetroStateStore };
