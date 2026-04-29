import { useCallback } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, ApiError } from "@/shared/lib/api-client";
import type { components } from "@/shared/types/api.d.ts";

export type ActionItemDto = components["schemas"]["ActionItemDto"];

export function usePreviousActions(retroId: string) {
  const queryClient = useQueryClient();

  const query = useQuery<ActionItemDto[]>({
    queryKey: ["previousActions", retroId],
    queryFn: async () => {
      const { data, response } = await apiClient.GET("/api/retro/{retroId}/previous-actions", {
        params: { path: { retroId } },
      });
      if (!response.ok) throw new ApiError(response.status, `Failed to fetch previous actions`);
      return data as ActionItemDto[];
    },
    enabled: !!retroId,
  });

  const invalidate = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: ["previousActions", retroId] });
  }, [queryClient, retroId]);

  return {
    ...query,
    invalidate,
  };
}
