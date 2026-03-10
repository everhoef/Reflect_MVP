import createClient from "openapi-fetch";
import type { paths } from "@/types/api.d.ts";

function getCsrfToken(): string | undefined {
  const raw = document.cookie
    .split("; ")
    .find((row) => row.startsWith("XSRF-TOKEN="))
    ?.split("=")[1];
  return raw ? decodeURIComponent(raw) : undefined;
}

export function csrfHeaders(): Record<string, string> {
  const token = getCsrfToken();
  return token ? { "X-XSRF-TOKEN": token } : {};
}

function csrfInjectingFetch(req: Request): Promise<Response> {
  const method = req.method.toUpperCase();
  if (method !== "GET" && method !== "HEAD" && method !== "OPTIONS") {
    const token = getCsrfToken();
    if (token) {
      const headers = new Headers(req.headers);
      headers.set("X-XSRF-TOKEN", token);
      return globalThis.fetch(new Request(req, { headers }));
    }
  }
  return globalThis.fetch(req);
}

export const apiClient = createClient<paths>({
  baseUrl: typeof window !== "undefined" ? window.location.origin : "http://localhost",
  fetch: csrfInjectingFetch,
});

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}
