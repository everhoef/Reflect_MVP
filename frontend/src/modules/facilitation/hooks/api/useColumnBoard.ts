import { useCallback } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, ApiError, csrfHeaders } from "@/shared/lib/api-client";
import type { components } from "@/shared/types/api.d.ts";

type ClusterGroupsDto = components["schemas"]["ClusterGroupsDto"];

export function useClusters(retroId: string, stepId: number) {
  const queryClient = useQueryClient();

  const query = useQuery<ClusterGroupsDto>({
    queryKey: ["clusters", retroId, stepId],
    queryFn: async () => {
      const { data, response } = await apiClient.GET("/api/retros/{retroId}/steps/{stepId}/clusters", {
        params: { path: { retroId, stepId } },
      });
      if (!response.ok) throw new ApiError(response.status, `Failed to fetch clusters: ${response.status}`);
      return data as ClusterGroupsDto;
    },
    enabled: !!retroId,
  });

  const invalidate = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: ["clusters", retroId, stepId] });
  }, [queryClient, retroId, stepId]);

  return { ...query, invalidate };
}

export async function submitColumnResponse(
  retroId: string,
  stepId: number,
  columnId: string,
  content: string,
): Promise<void> {
  const base = typeof window !== "undefined" ? window.location.origin : "http://localhost";
  const response = await globalThis.fetch(
    `${base}/api/retros/${retroId}/steps/${stepId}/responses/column`,
    {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded", ...csrfHeaders() },
      body: new URLSearchParams({ columnId, content }).toString(),
    },
  );
  if (!response.ok) throw new ApiError(response.status, `Failed to submit response: ${response.status}`);
}

export async function toggleVote(retroId: string, responseId: string): Promise<void> {
  const { response } = await apiClient.POST("/api/retros/{retroId}/responses/{responseId}/vote", {
    params: { path: { retroId, responseId } },
  });
  if (!response.ok) throw new ApiError(response.status, `Failed to toggle vote: ${response.status}`);
}

export async function updateResponse(retroId: string, responseId: string, content: string): Promise<void> {
  const { response } = await apiClient.PUT("/api/retros/{retroId}/responses/{responseId}", {
    params: { path: { retroId, responseId }, query: { content } },
  });
  if (!response.ok) throw new ApiError(response.status, `Failed to update response: ${response.status}`);
}

export async function mergeResponses(retroId: string, stepId: number, responseIds: string[]): Promise<void> {
  const { response } = await apiClient.POST("/api/retros/{retroId}/steps/{stepId}/clusters/merge", {
    params: { path: { retroId, stepId } },
    body: { responseIds },
  });
  if (!response.ok) throw new ApiError(response.status, `Failed to merge responses: ${response.status}`);
}

export async function unmergeResponse(retroId: string, stepId: number, responseId: string): Promise<void> {
  const { response } = await apiClient.POST("/api/retros/{retroId}/steps/{stepId}/clusters/unmerge", {
    params: { path: { retroId, stepId } },
    body: { responseId },
  });
  if (!response.ok) throw new ApiError(response.status, `Failed to unmerge response: ${response.status}`);
}
