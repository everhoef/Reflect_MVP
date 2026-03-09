import { useState, useCallback } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useSSE } from "@/hooks/useSSE";
import { EventType } from "@/types/events";
import type { StepComponentProps } from "@/components/ComponentRouter";
import type { components } from "@/types/api.d.ts";

type RatingResponseDto = components["schemas"]["RatingResponseDto"];

interface RatingScaleConfig {
  min: number;
  max: number;
  step: number;
  labels: string[];
  inputType: "radio" | "slider";
  allowComment: boolean;
  commentMaxLength: number;
}

interface MeResponse {
  isAuthenticated: boolean;
  isGuest: boolean;
  authType: string;
  user?: {
    id: string;
    displayName: string;
    role: string;
  };
}

function getCsrfToken(): string | undefined {
  const raw = document.cookie
    .split("; ")
    .find((row) => row.startsWith("XSRF-TOKEN="))
    ?.split("=")[1];
  return raw ? decodeURIComponent(raw) : undefined;
}

function formHeaders(): HeadersInit {
  const csrf = getCsrfToken();
  return csrf ? { "X-XSRF-TOKEN": csrf } : {};
}

async function fetchMe(): Promise<MeResponse> {
  const res = await fetch("/api/me");
  if (!res.ok) throw new Error(`Failed to fetch current user: ${res.status}`);
  return res.json() as Promise<MeResponse>;
}

async function fetchMyRating(
  retroId: string,
  stepId: number
): Promise<RatingResponseDto | null> {
  const res = await fetch(`/api/retro/${retroId}/step/${stepId}/response/rating/me`);
  if (res.status === 404) return null;
  if (!res.ok) return null;
  return res.json() as Promise<RatingResponseDto>;
}

export function RatingScale({ retroId, stepId, componentConfig }: StepComponentProps) {
  const queryClient = useQueryClient();
  const config = componentConfig as unknown as Partial<RatingScaleConfig>;

  const min = config.min ?? 1;
  const max = config.max ?? 10;
  const scaleStep = config.step ?? 1;
  const labels = config.labels ?? [];
  const allowComment = config.allowComment === true;
  const commentMaxLength = config.commentMaxLength ?? 500;

  const minLabel = labels[0] ?? null;
  const maxLabel = labels[1] ?? null;

  const values: number[] = [];
  for (let v = min; v <= max; v += scaleStep) {
    values.push(v);
  }

  const [selectedRating, setSelectedRating] = useState<number | null>(null);
  const [comment, setComment] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(false);

  const { data: me } = useQuery<MeResponse>({
    queryKey: ["me"],
    queryFn: fetchMe,
    staleTime: Infinity,
  });

  const { data: existingRating } = useQuery<RatingResponseDto | null>({
    queryKey: ["ratingResponse", retroId, stepId],
    queryFn: () => fetchMyRating(retroId, stepId),
    enabled: !!retroId,
  });

  const invalidate = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: ["ratingResponse", retroId, stepId] });
  }, [queryClient, retroId, stepId]);

  useSSE(retroId, {
    [EventType.NOTE_ADDED]: invalidate,
    [EventType.NOTE_UPDATED]: invalidate,
  });

  const currentParticipantId = me?.user?.id;

  const hasSubmitted =
    submitted ||
    (existingRating != null &&
      existingRating.participantId === currentParticipantId);

  const effectiveRating = submitted
    ? selectedRating
    : existingRating?.rating ?? null;

  const effectiveComment = submitted
    ? comment
    : existingRating?.comment ?? "";

  const isOwnResponse =
    existingRating?.participantId === currentParticipantId;
  const isPrivate =
    existingRating != null && existingRating.visible === false && !isOwnResponse;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (selectedRating === null) return;
    setSubmitting(true);
    try {
      const params = new URLSearchParams({
        rating: String(selectedRating),
      });
      if (allowComment && comment.trim()) {
        params.set("comment", comment.trim());
      }
      await fetch(
        `/api/retro/${retroId}/step/${stepId}/response/rating?${params.toString()}`,
        {
          method: "POST",
          headers: formHeaders(),
        }
      );
      setSubmitted(true);
      invalidate();
    } finally {
      setSubmitting(false);
    }
  };

  if (isPrivate) {
    return (
      <div className="flex flex-col items-center justify-center h-64 rounded-xl border-2 border-dashed border-blue-200 bg-blue-50">
        <div className="text-4xl mb-3">🔒</div>
        <p className="text-blue-700 font-semibold text-lg">Waiting for reveal</p>
        <p className="text-blue-500 text-sm mt-1">
          The facilitator will reveal all ratings shortly
        </p>
      </div>
    );
  }

  if (hasSubmitted) {
    return (
      <div className="bg-white rounded-xl border border-gray-200 p-8 text-center space-y-4">
        <div className="flex items-center justify-center w-12 h-12 rounded-full bg-green-100 mx-auto">
          <svg
            className="w-6 h-6 text-green-600"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M5 13l4 4L19 7"
            />
          </svg>
        </div>
        <p className="text-gray-800 font-semibold text-lg">Response submitted</p>
        <div className="flex items-center justify-center gap-1 pt-2">
          {values.map((v) => (
            <div
              key={v}
              className={[
                "w-9 h-9 rounded-lg flex items-center justify-center text-sm font-bold border-2 transition-all",
                v === effectiveRating
                  ? "bg-blue-600 border-blue-600 text-white scale-110 shadow-md"
                  : "bg-gray-50 border-gray-200 text-gray-400",
              ].join(" ")}
            >
              {v}
            </div>
          ))}
        </div>
        {minLabel && maxLabel && (
          <p className="text-xs text-gray-400">
            {minLabel} ({min}) → {maxLabel} ({max})
          </p>
        )}
        {effectiveComment && (
          <p className="text-sm text-gray-600 italic mt-2 max-w-xs mx-auto">
            "{effectiveComment}"
          </p>
        )}
      </div>
    );
  }

  return (
    <div className="bg-white rounded-xl border border-gray-200 p-8">
      <form onSubmit={(e) => void handleSubmit(e)} className="space-y-6">
        <div className="flex justify-center gap-2 flex-wrap">
          {values.map((v) => (
            <label
              key={v}
              className={[
                "relative flex flex-col items-center cursor-pointer group select-none",
                submitting ? "opacity-50 cursor-not-allowed" : "",
              ].join(" ")}
            >
              <input
                type="radio"
                name="rating"
                value={v}
                checked={selectedRating === v}
                onChange={() => setSelectedRating(v)}
                disabled={submitting}
                className="absolute inset-0 w-full h-full opacity-0 cursor-pointer"
              />
              <div
                className={[
                  "w-10 h-10 rounded-lg flex items-center justify-center text-base font-bold border-2 transition-all",
                  selectedRating === v
                    ? "bg-blue-600 border-blue-600 text-white scale-110 shadow-md"
                    : "bg-gray-50 border-gray-200 text-gray-700 group-hover:border-blue-400 group-hover:bg-blue-50",
                ].join(" ")}
              >
                {v}
              </div>
            </label>
          ))}
        </div>

        {minLabel && maxLabel ? (
          <div className="flex justify-between text-xs text-gray-400 px-1">
            <span>
              {minLabel} ({min})
            </span>
            <span>
              {maxLabel} ({max})
            </span>
          </div>
        ) : (
          <p className="text-center text-xs text-gray-400">
            Scale: {min} (low) to {max} (high)
          </p>
        )}

        {allowComment && (
          <div className="text-left space-y-1">
            <label
              htmlFor={`rating-comment-${stepId}`}
              className="block text-sm font-medium text-gray-700"
            >
              Why did you choose this rating?{" "}
              <span className="text-gray-400 font-normal">(optional)</span>
            </label>
            <textarea
              id={`rating-comment-${stepId}`}
              name="comment"
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              maxLength={commentMaxLength}
              rows={2}
              disabled={submitting}
              placeholder="Share your reasoning..."
              className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm resize-none focus:outline-none focus:ring-2 focus:ring-blue-400 disabled:opacity-50"
            />
          </div>
        )}

        <div className="flex justify-center">
          <button
            type="submit"
            disabled={selectedRating === null || submitting}
            className="px-8 py-3 rounded-lg bg-blue-600 text-white font-medium hover:bg-blue-700 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
          >
            {submitting ? "Submitting…" : "Submit My Rating"}
          </button>
        </div>
      </form>
    </div>
  );
}
