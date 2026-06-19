import { useQuery } from "@tanstack/react-query";

export interface MeResponse {
  isAuthenticated: boolean;
  isGuest: boolean;
  authType: string;
  user?: {
    id: string;
    displayName: string;
    role: string;
  };
}

async function fetchCurrentUser(): Promise<MeResponse | null> {
  const res = await fetch("/api/me");
  if (res.status === 401) {
    return null;
  }
  if (!res.ok) {
    throw new Error(`Failed to fetch current user: ${res.status}`);
  }
  return res.json() as Promise<MeResponse>;
}

export function useCurrentUser() {
  return useQuery<MeResponse | null>({
    queryKey: ["me"],
    queryFn: fetchCurrentUser,
    staleTime: 60 * 1000,
  });
}
