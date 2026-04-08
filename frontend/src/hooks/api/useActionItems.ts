import { useCallback } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, ApiError } from "@/lib/api-client";
import type { components } from "@/types/api.d.ts";

export type ActionItemDto = components["schemas"]["ActionItemDto"];
export type CreateActionItemRequest = components["schemas"]["CreateActionItemRequest"];
export type UpdateActionItemRequest = components["schemas"]["UpdateActionItemRequest"];

export function useActionItems(retroId: string) {
  const queryClient = useQueryClient();

  const query = useQuery<ActionItemDto[]>({
    queryKey: ["actionItems", retroId],
    queryFn: async () => {
      const { data, response } = await apiClient.GET("/api/retro/{retroId}/actions", {
        params: { path: { retroId } },
      });
      if (!response.ok) throw new ApiError(response.status, `Failed to fetch action items: ${response.status}`);
      return data as ActionItemDto[];
    },
    enabled: !!retroId,
  });

  const invalidate = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: ["actionItems", retroId] });
  }, [queryClient, retroId]);

  const createActionItem = useMutation({
    mutationFn: async (req: CreateActionItemRequest) => {
      const { data, response } = await apiClient.POST("/api/retro/{retroId}/actions", {
        params: { path: { retroId } },
        body: req,
      });
      if (!response.ok) throw new ApiError(response.status, `Failed to create action item`);
      return data;
    },
    onSuccess: invalidate,
  });

  const updateActionItem = useMutation({
    mutationFn: async ({ actionId, req }: { actionId: string; req: UpdateActionItemRequest }) => {
      const { data, response } = await apiClient.PATCH("/api/retro/{retroId}/actions/{actionId}", {
        params: { path: { retroId, actionId } },
        body: req,
      });
      if (!response.ok) throw new ApiError(response.status, `Failed to update action item`);
      return data;
    },
    onSuccess: invalidate,
  });

  const deleteActionItem = useMutation({
    mutationFn: async (actionId: string) => {
      const { response } = await apiClient.DELETE("/api/retro/{retroId}/actions/{actionId}", {
        params: { path: { retroId, actionId } },
      });
      if (!response.ok) throw new ApiError(response.status, `Failed to delete action item`);
    },
    onSuccess: invalidate,
  });

  return {
    ...query,
    invalidate,
    createActionItem: createActionItem.mutateAsync,
    updateActionItem: updateActionItem.mutateAsync,
    deleteActionItem: deleteActionItem.mutateAsync,
  };
}
