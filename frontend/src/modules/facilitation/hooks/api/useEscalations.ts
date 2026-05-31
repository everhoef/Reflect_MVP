import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { create } from 'zustand';
import type { components } from '@/shared/types/api.d.ts';
import { apiClient, ApiError } from "@/shared/lib/api-client";
import { useAppliedVersionStore } from '@/modules/facilitation/hooks/api/useRetro';

export type EscalatedItemDto = components["schemas"]["EscalatedItemDto"];
export type EscalationVoteResultDto = components["schemas"]["EscalationVoteResultDto"];
type EscalationVoteData = {
  escalationId?: string | undefined;
  voteCount?: number | undefined;
  threshold?: number | undefined;
  thresholdMet?: boolean | undefined;
};
type SyncVersionedResponse<T> = { syncVersion?: number | null; data?: T | null };

export async function fetchEscalations(retroId?: string): Promise<EscalatedItemDto[]> {
  if (!retroId) return [];
  const { data, response } = await apiClient.GET("/api/retros/{retroId}/escalations", {
    params: { path: { retroId } },
  });
  if (!response.ok) throw new ApiError(response.status, `Failed to fetch escalations`);

  const wrapped = data as EscalatedItemDto[] | SyncVersionedResponse<EscalatedItemDto[]>;
  if (typeof wrapped === 'object' && wrapped !== null && ('data' in wrapped || 'syncVersion' in wrapped)) {
    const responseData = wrapped as SyncVersionedResponse<EscalatedItemDto[]>;
    useAppliedVersionStore.getState().markAppliedVersion(retroId, responseData.syncVersion ?? null);
    return responseData.data ?? [];
  }

  return wrapped as EscalatedItemDto[];
}

export const useVoteStore = create<{
  votedMap: Record<string, boolean>;
  setVote: (id: string, voted: boolean) => void;
}>((set) => ({
  votedMap: {},
  setVote: (id, voted) => set((state) => ({
    votedMap: { ...state.votedMap, [id]: voted }
  })),
}));

export function useEscalations(retroId?: string) {
  const queryClient = useQueryClient();
  const setVote = useVoteStore((state) => state.setVote);

  const applyVoteUpdate = (payload: EscalationVoteData) => {
    if (!retroId || !payload.escalationId) {
      return;
    }

    queryClient.setQueryData<EscalatedItemDto[] | undefined>(['escalations', retroId], (current) => {
      if (!current) {
        return current;
      }

      return current.map((escalation) => {
        if (escalation.id !== payload.escalationId) {
          return escalation;
        }

        const nextEscalation: EscalatedItemDto = {
          ...escalation,
          ...(payload.voteCount !== undefined ? { voteCount: payload.voteCount } : {}),
          ...(payload.threshold !== undefined ? { threshold: payload.threshold } : {}),
          ...(payload.thresholdMet !== undefined ? { thresholdMet: payload.thresholdMet } : {}),
        };

        return nextEscalation;
      });
    });
  };

  const query = useQuery<EscalatedItemDto[]>({
    queryKey: ['escalations', retroId],
    queryFn: () => fetchEscalations(retroId),
    enabled: !!retroId,
  });

  const escalateMutation = useMutation({
    mutationFn: async ({ actionId, problemDescription }: { actionId: string; problemDescription: string }) => {
      if (!retroId) throw new Error('No retro ID');
      const { data, error, response } = await apiClient.POST("/api/retros/{retroId}/actions/{actionId}/escalations", {
        params: { path: { retroId, actionId } },
        body: { problemDescription },
      });
      if (!response.ok) {
        let errorMessage = "Failed to escalate action";
        const err: unknown = error;
        if (typeof err === "object" && err !== null && "message" in err && typeof err.message === "string") {
          errorMessage = err.message;
        }
        throw new ApiError(response.status, errorMessage);
      }
      return data;
    },
    onSuccess: (data) => {
      const result = data as EscalatedItemDto;
      if (result && result.id) {
        useVoteStore.getState().setVote(result.id, false);
      }
      void queryClient.invalidateQueries({ queryKey: ['actionItems', retroId] });
      void queryClient.invalidateQueries({ queryKey: ['escalations', retroId] });
    },
  });

  const voteMutation = useMutation({
    mutationFn: async (escalationId: string) => {
      if (!retroId) throw new Error('No retro ID');
      const { data, response } = await apiClient.POST("/api/retros/{retroId}/escalations/{escalationId}/vote", {
        params: { path: { retroId, escalationId } },
      });
      if (!response.ok) throw new ApiError(response.status, `Failed to toggle vote`);
      
      const result = data as EscalationVoteResultDto;
      if (result && result.escalationId && typeof result.voted === 'boolean') {
        setVote(result.escalationId, result.voted);
      }
      return result;
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['escalations', retroId] });
    },
  });

  const getKnownVoteState = (id: string) => {
    return useVoteStore.getState().votedMap[id];
  };

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['escalations', retroId] });
  };

  return {
    escalations: query.data || [],
    isLoading: query.isLoading,
    escalateAction: escalateMutation.mutateAsync,
    isEscalating: escalateMutation.isPending,
    toggleVote: voteMutation.mutateAsync,
    isVoting: voteMutation.isPending,
    getKnownVoteState,
    invalidate,
    applyVoteUpdate,
  };
}
