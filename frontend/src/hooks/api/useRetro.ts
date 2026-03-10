import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, ApiError } from "@/lib/api-client";
import type { components } from "@/types/api.d.ts";

type RetroStateDto = components["schemas"]["RetroStateDto"];
type ParticipantDto = components["schemas"]["ParticipantDto"];

export function useRetroState(retroId: string | undefined) {
  return useQuery<RetroStateDto>({
    queryKey: ["retroState", retroId],
    queryFn: async () => {
      const { data, response } = await apiClient.GET("/api/retro/{retroId}/state", {
        params: { path: { retroId: retroId! } },
      });
      if (!response.ok) throw new ApiError(response.status, `Failed to fetch retro state: ${response.status}`);
      return data as RetroStateDto;
    },
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
    queryFn: async () => {
      const { data, response } = await apiClient.GET("/api/retro/{retroId}/participants", {
        params: { path: { retroId: retroId! } },
      });
      if (!response.ok) throw new ApiError(response.status, `Failed to fetch participants: ${response.status}`);
      return (data ?? []) as ParticipantDto[];
    },
    enabled: !!retroId,
  });
}

export function useNextStep(retroId: string | undefined) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      const { response } = await apiClient.POST("/api/retro/{retroId}/next", {
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
      const { response } = await apiClient.POST("/api/retro/{retroId}/start", {
        params: { path: { retroId: retroId! } },
      });
      if (!response.ok) throw new ApiError(response.status, `Failed to start session: ${response.status}`);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["retroState", retroId] });
    },
  });
}
