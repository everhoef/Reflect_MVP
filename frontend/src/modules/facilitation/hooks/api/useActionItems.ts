import { useCallback } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, ApiError } from "@/shared/lib/api-client";
import type { components } from "@/shared/types/api.d.ts";
import { useAppliedVersionStore } from '@/modules/facilitation/hooks/api/useRetro';

export type ActionItemDto = components["schemas"]["ActionItemDto"];
export type CreateActionItemRequest = components["schemas"]["CreateActionItemRequest"];
export type UpdateActionItemRequest = components["schemas"]["UpdateActionItemRequest"];
type SyncVersionedResponse<T> = { syncVersion?: number | null; data?: T | null };

export async function fetchActionItems(retroId: string): Promise<ActionItemDto[]> {
  const { data, response } = await apiClient.GET("/api/retros/{retroId}/actions", {
    params: { path: { retroId } },
  });
  if (!response.ok) throw new ApiError(response.status, `Failed to fetch action items: ${response.status}`);

  const wrapped = data as ActionItemDto[] | SyncVersionedResponse<ActionItemDto[]>;
  if (typeof wrapped === 'object' && wrapped !== null && ('data' in wrapped || 'syncVersion' in wrapped)) {
    const responseData = wrapped as SyncVersionedResponse<ActionItemDto[]>;
    useAppliedVersionStore.getState().markAppliedVersion(retroId, responseData.syncVersion ?? null);
    return responseData.data ?? [];
  }

  return wrapped as ActionItemDto[];
}

export function useActionItems(retroId: string) {
  const queryClient = useQueryClient();

  const query = useQuery<ActionItemDto[]>({
    queryKey: ["actionItems", retroId],
    queryFn: () => fetchActionItems(retroId),
    enabled: !!retroId,
  });

  const invalidate = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: ["actionItems", retroId] });
  }, [queryClient, retroId]);

  const createActionItem = useMutation({
    mutationFn: async (req: CreateActionItemRequest) => {
      const { data, response } = await apiClient.POST("/api/retros/{retroId}/actions", {
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
      const { data, response } = await apiClient.PATCH("/api/retros/{retroId}/actions/{actionId}", {
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
      const { response } = await apiClient.DELETE("/api/retros/{retroId}/actions/{actionId}", {
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
