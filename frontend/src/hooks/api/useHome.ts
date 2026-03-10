import { apiClient, ApiError } from "@/lib/api-client";
import type { components } from "@/types/api.d.ts";

type CreateRetroRequest = components["schemas"]["CreateRetroRequest"];
type JoinRetroRequest = components["schemas"]["JoinRetroRequest"];

interface CreateRetroResponse {
  redirectUrl: string;
}

interface JoinRetroResponse {
  redirectUrl: string;
}

export async function createRetrospective(req: CreateRetroRequest): Promise<CreateRetroResponse> {
  const { data, response } = await apiClient.POST("/api/retro/create", {
    body: req,
  });
  if (!response.ok) throw new ApiError(response.status, "Failed to create session");
  return (data as unknown) as CreateRetroResponse;
}

export async function joinRetrospective(req: JoinRetroRequest): Promise<JoinRetroResponse> {
  const { data, response } = await apiClient.POST("/api/retro/join", {
    body: req,
  });
  if (!response.ok) throw new ApiError(response.status, "Failed to join session");
  return (data as unknown) as JoinRetroResponse;
}

export async function leaveActiveSessions(): Promise<void> {
  const { response } = await apiClient.POST("/api/retro/leave-active-sessions", {});
  if (!response.ok) throw new ApiError(response.status, "Failed to leave sessions");
}
