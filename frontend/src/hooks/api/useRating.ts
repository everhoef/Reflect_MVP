import { useCallback } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, ApiError, csrfHeaders } from "@/lib/api-client";
import type { components } from "@/types/api.d.ts";

type RatingResponseDto = components["schemas"]["RatingResponseDto"];

export function useRatingResponses(retroId: string, stepId: number) {
  const queryClient = useQueryClient();

  const query = useQuery<RatingResponseDto[]>({
    queryKey: ["ratingResponses", retroId, stepId],
    queryFn: async () => {
      const { data, response } = await apiClient.GET("/api/retro/{retroId}/step/{stepId}/response/rating", {
        params: { path: { retroId, stepId } },
      });
      if (!response.ok) return [];
      return (data ?? []) as RatingResponseDto[];
    },
    enabled: !!retroId,
  });

  const invalidate = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: ["ratingResponses", retroId, stepId] });
  }, [queryClient, retroId, stepId]);

  return { ...query, invalidate };
}

export async function submitRatingResponse(
  retroId: string,
  stepId: number,
  rating: number,
  comment?: string,
): Promise<void> {
  const params: Record<string, string> = { rating: String(rating) };
  if (comment) params.comment = comment;
  const base = typeof window !== "undefined" ? window.location.origin : "http://localhost";
  const response = await globalThis.fetch(
    `${base}/api/retro/${retroId}/step/${stepId}/response/rating`,
    {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded", ...csrfHeaders() },
      body: new URLSearchParams(params).toString(),
    },
  );
  if (!response.ok) throw new ApiError(response.status, `Failed to submit rating: ${response.status}`);
}
