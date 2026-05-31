import { create } from "zustand";
import { type AssistantMessage, type AssistantState, HISTORY_MAX_SIZE } from '@/shared/types/assistantState';

export interface AssistantStoreActions {
  pushMessage: (retroId: string, stepId: number, stepTitle: string, publicText: string) => void;
  setCoachingNote: (note: string | null) => void;
  bootstrapState: (state: AssistantState) => void;
  clearAssistant: () => void;
}

export type AssistantStoreState = AssistantState & AssistantStoreActions;

const initialState: AssistantState = {
  current: null,
  history: [],
  facilitatorCoachingNote: null,
};

export const useAssistantStore = create<AssistantStoreState>()((set) => ({
  ...initialState,

  pushMessage: (retroId, stepId, stepTitle, publicText) =>
    set((state) => {
      const next: AssistantMessage = { retroId, stepId, stepTitle, publicText };
      if (state.current === null) {
        return { current: next, history: [] };
      }
      const updated = [state.current, ...state.history].slice(0, HISTORY_MAX_SIZE);
      return { current: next, history: updated };
    }),

  setCoachingNote: (note) => set({ facilitatorCoachingNote: note }),

  bootstrapState: (state) => set({ ...state }),

  clearAssistant: () => set({ ...initialState }),
}));
