import { apiClient, ApiError } from "@/shared/lib/api-client";
import type { components } from "@/shared/types/api.d.ts";

type CreateRetroRequest = components["schemas"]["CreateRetroRequest"];

interface CreateRetroResponse {
  redirectUrl: string;
}

interface JoinRetroResponse {
  redirectUrl: string;
}

export async function createRetrospective(req: CreateRetroRequest): Promise<CreateRetroResponse> {
  const { data, response } = await apiClient.POST("/api/retros", {
    body: req,
  });
  if (!response.ok) throw new ApiError(response.status, "Failed to create session");
  return (data as unknown) as CreateRetroResponse;
}

export async function joinRetrospective(retroId: string): Promise<JoinRetroResponse> {
  const { data, response } = await apiClient.POST("/api/retros/{retroId}/participants", {
    params: { path: { retroId } },
  });
  if (!response.ok) throw new ApiError(response.status, "Failed to join session");
  return (data as unknown) as JoinRetroResponse;
}

export async function leaveActiveSessions(): Promise<void> {
  const { response } = await apiClient.DELETE("/api/me/retros/active", {});
  if (!response.ok) throw new ApiError(response.status, "Failed to leave sessions");
}
