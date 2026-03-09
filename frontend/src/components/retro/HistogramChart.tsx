import { useCallback } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useSSE } from "@/hooks/useSSE";
import { EventType } from "@/types/events";
import type { StepComponentProps } from "@/components/ComponentRouter";
import type { components } from "@/types/api.d.ts";

type RatingResponseDto = components["schemas"]["RatingResponseDto"];

interface HistogramConfig {
  min: number;
  max: number;
  step: number;
  labels: string[];
}

async function fetchRatingResponses(
  retroId: string,
  stepId: number
): Promise<RatingResponseDto[]> {
  const res = await fetch(`/api/retro/${retroId}/step/${stepId}/response/rating`);
  if (!res.ok) return [];
  const data: unknown = await res.json();
  if (!Array.isArray(data)) return [];
  return data as RatingResponseDto[];
}

export function HistogramChart({
  retroId,
  stepId,
  componentConfig,
}: StepComponentProps) {
  const queryClient = useQueryClient();
  const config = componentConfig as unknown as Partial<HistogramConfig>;

  const min = config.min ?? 1;
  const max = config.max ?? 10;
  const scaleStep = config.step ?? 1;
  const labels = config.labels ?? [];

  const minLabel = labels[0] ?? null;
  const maxLabel = labels[1] ?? null;

  const values: number[] = [];
  for (let v = min; v <= max; v += scaleStep) {
    values.push(v);
  }

  const { data: responses = [] } = useQuery<RatingResponseDto[]>({
    queryKey: ["ratingResponses", retroId, stepId],
    queryFn: () => fetchRatingResponses(retroId, stepId),
    enabled: !!retroId,
  });

  const invalidate = useCallback(() => {
    void queryClient.invalidateQueries({
      queryKey: ["ratingResponses", retroId, stepId],
    });
  }, [queryClient, retroId, stepId]);

  useSSE(retroId, {
    [EventType.NOTE_ADDED]: invalidate,
    [EventType.NOTE_UPDATED]: invalidate,
  });

  const countsByValue = new Map<number, number>();
  for (const v of values) {
    countsByValue.set(v, 0);
  }
  for (const r of responses) {
    if (r.rating != null) {
      const current = countsByValue.get(r.rating) ?? 0;
      countsByValue.set(r.rating, current + 1);
    }
  }

  const totalResponses = responses.length;
  const maxCount = Math.max(...Array.from(countsByValue.values()), 1);

  const average =
    totalResponses > 0
      ? responses.reduce((sum, r) => sum + (r.rating ?? 0), 0) / totalResponses
      : null;

  if (totalResponses === 0) {
    return (
      <div className="bg-white rounded-xl border border-gray-200 p-8 flex flex-col items-center justify-center gap-4 min-h-[16rem]">
        <div className="text-5xl select-none">📊</div>
        <p className="text-gray-500 font-medium">No responses yet</p>
        <p className="text-gray-400 text-sm">
          Waiting for participants to submit ratings…
        </p>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-xl border border-gray-200 p-6 space-y-6">
      <div className="flex items-center justify-between">
        <h3 className="text-base font-semibold text-gray-800">
          Rating Distribution
        </h3>
        {average !== null && (
          <div className="flex items-center gap-2">
            <span className="text-xs text-gray-500 uppercase tracking-wide font-medium">
              Average
            </span>
            <span className="text-2xl font-bold text-blue-600 tabular-nums">
              {average.toFixed(1)}
            </span>
            <span className="text-xs text-gray-400">/ {max}</span>
          </div>
        )}
      </div>

      <div className="space-y-1">
        {values.map((v) => {
          const count = countsByValue.get(v) ?? 0;
          const pct = (count / maxCount) * 100;
          const isHighest = count === maxCount && count > 0;

          return (
            <div key={v} className="flex items-center gap-3 group">
              <span className="w-6 text-right text-sm font-medium text-gray-600 shrink-0 tabular-nums">
                {v}
              </span>

              <div className="flex-1 h-7 bg-gray-100 rounded-md overflow-hidden relative">
                <div
                  className={[
                    "h-full rounded-md transition-all duration-500 ease-out",
                    isHighest
                      ? "bg-blue-500"
                      : count > 0
                        ? "bg-blue-300"
                        : "bg-transparent",
                  ].join(" ")}
                  style={{ width: `${pct}%` }}
                />
                {count > 0 && (
                  <span
                    className={[
                      "absolute inset-0 flex items-center px-2 text-xs font-semibold tabular-nums transition-colors",
                      pct > 25 ? "text-white" : "text-gray-700",
                    ].join(" ")}
                  >
                    {count}
                  </span>
                )}
              </div>

              <span className="w-10 text-right text-xs text-gray-400 shrink-0 tabular-nums">
                {count > 0
                  ? `${Math.round((count / totalResponses) * 100)}%`
                  : ""}
              </span>
            </div>
          );
        })}
      </div>

      {minLabel != null && maxLabel != null && (
        <div className="flex justify-between text-xs text-gray-400 px-9">
          <span>
            {minLabel} ({min})
          </span>
          <span>
            {maxLabel} ({max})
          </span>
        </div>
      )}

      <p className="text-xs text-gray-400 text-right">
        {totalResponses} {totalResponses === 1 ? "response" : "responses"}
      </p>

      {responses.some((r) => r.comment) && (
        <div className="space-y-2 border-t border-gray-100 pt-4">
          <h4 className="text-xs font-semibold text-gray-500 uppercase tracking-wide">
            Comments
          </h4>
          {responses
            .filter((r) => r.comment)
            .map((r) => (
              <div key={r.id ?? r.participantId} className="flex items-start gap-2">
                <span className="text-xs font-semibold text-blue-600 tabular-nums shrink-0">
                  {r.rating}
                </span>
                <p className="text-sm text-gray-700">{r.comment}</p>
              </div>
            ))}
        </div>
      )}
    </div>
  );
}
