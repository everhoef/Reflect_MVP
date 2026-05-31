import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { create } from "zustand";
import { apiClient, ApiError } from "@/shared/lib/api-client";
import type { components } from "@/shared/types/api.d.ts";

type RetroStateDto = components["schemas"]["RetroStateDto"] & { syncVersion?: number | null };
type ParticipantDto = components["schemas"]["ParticipantDto"];
type TimerStateDto = components["schemas"]["TimerStateDto"];
type SyncVersionedResponse<T> = { syncVersion?: number | null; data?: T | null };

export const useAppliedVersionStore = create<{
  appliedVersionByRetroId: Record<string, number>;
  markAppliedVersion: (retroId: string, syncVersion: number | null | undefined) => void;
}>((set) => ({
  appliedVersionByRetroId: {},
  markAppliedVersion: (retroId, syncVersion) => {
    if (syncVersion == null) {
      return;
    }

    set((state) => ({
      appliedVersionByRetroId: {
        ...state.appliedVersionByRetroId,
        [retroId]: Math.max(state.appliedVersionByRetroId[retroId] ?? syncVersion, syncVersion),
      },
    }));
  },
}));

export function useAppliedVersion(retroId: string | undefined): number | null {
  return useAppliedVersionStore((state) =>
    retroId ? state.appliedVersionByRetroId[retroId] ?? null : null
  );
}

function unwrapSyncVersionedData<T>(value: T | SyncVersionedResponse<T>): {
  data: T;
  syncVersion: number | null;
} {
  if (typeof value === "object" && value !== null && ("data" in value || "syncVersion" in value)) {
    const wrapped = value as SyncVersionedResponse<T>;
    return {
      data: (wrapped.data ?? null) as T,
      syncVersion: wrapped.syncVersion ?? null,
    };
  }

  return {
    data: value as T,
    syncVersion: null,
  };
}

export async function fetchRetroState(retroId: string): Promise<RetroStateDto> {
  const { data, response } = await apiClient.GET("/api/retros/{retroId}", {
    params: { path: { retroId } },
  });
  if (!response.ok) throw new ApiError(response.status, `Failed to fetch retro state: ${response.status}`);
  const state = data as RetroStateDto;
  useAppliedVersionStore.getState().markAppliedVersion(retroId, state.syncVersion ?? null);
  return state;
}

export async function fetchParticipants(retroId: string): Promise<ParticipantDto[]> {
  const { data, response } = await apiClient.GET("/api/retros/{retroId}/participants", {
    params: { path: { retroId } },
  });
  if (!response.ok) throw new ApiError(response.status, `Failed to fetch participants: ${response.status}`);
  const unwrapped = unwrapSyncVersionedData<ParticipantDto[]>((data ?? []) as ParticipantDto[] | SyncVersionedResponse<ParticipantDto[]>);
  useAppliedVersionStore.getState().markAppliedVersion(retroId, unwrapped.syncVersion);
  return unwrapped.data ?? [];
}

export async function fetchTimerState(retroId: string): Promise<TimerStateDto | null> {
  const { data, response } = await apiClient.GET("/api/retros/{retroId}/timer", {
    params: { path: { retroId } },
  });
  if (response.status === 204) return null;
  if (!response.ok) throw new ApiError(response.status, `Failed to fetch timer: ${response.status}`);
  const unwrapped = unwrapSyncVersionedData<TimerStateDto | null>((data ?? null) as TimerStateDto | SyncVersionedResponse<TimerStateDto | null>);
  useAppliedVersionStore.getState().markAppliedVersion(retroId, unwrapped.syncVersion);
  return unwrapped.data ?? null;
}

export function useRetroState(retroId: string | undefined) {
  return useQuery<RetroStateDto>({
    queryKey: ["retroState", retroId],
    queryFn: () => fetchRetroState(retroId!),
    enabled: !!retroId,
    retry: (failureCount, err) => {
      if (err instanceof ApiError && err.status === 404) return false;
      return failureCount < 3;
    },
  });
}

export function useParticipants(retroId: string | undefined) {
  return useQuery<ParticipantDto[]>({
    queryKey: ["participants", retroId],
    queryFn: () => fetchParticipants(retroId!),
    enabled: !!retroId,
  });
}

export function useNextStep(retroId: string | undefined) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      const { response } = await apiClient.POST("/api/retros/{retroId}/advance", {
        params: { path: { retroId: retroId! } },
      });
      if (!response.ok) throw new ApiError(response.status, `Failed to advance step: ${response.status}`);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["retroState", retroId] });
    },
  });
}

export function useStartSession(retroId: string | undefined) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      const { response } = await apiClient.POST("/api/retros/{retroId}/start", {
        params: { path: { retroId: retroId! } },
      });
      if (!response.ok) throw new ApiError(response.status, `Failed to start session: ${response.status}`);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["retroState", retroId] });
    },
  });
}

export function useTimer(retroId: string | undefined) {
  return useQuery<TimerStateDto | null>({
    queryKey: ["timer", retroId],
    queryFn: () => fetchTimerState(retroId!),
    enabled: !!retroId,
    staleTime: 0,
  });
}

export function usePauseTimer(retroId: string | undefined) {
  return useMutation({
    mutationFn: async () => {
      const { response } = await apiClient.POST("/api/retros/{retroId}/timer/pause", {
        params: { path: { retroId: retroId! } },
      });
      if (!response.ok) throw new ApiError(response.status, `Failed to pause timer: ${response.status}`);
    },
  });
}

export function useResumeTimer(retroId: string | undefined) {
  return useMutation({
    mutationFn: async () => {
      const { response } = await apiClient.POST("/api/retros/{retroId}/timer/resume", {
        params: { path: { retroId: retroId! } },
      });
      if (!response.ok) throw new ApiError(response.status, `Failed to resume timer: ${response.status}`);
    },
  });
}
