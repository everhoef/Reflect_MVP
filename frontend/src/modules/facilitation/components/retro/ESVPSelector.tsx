import { useState, useCallback } from "react";
import { useSSESubscription } from "@/shared/hooks/useSSEContext";
import { EventType } from "@/shared/types/events";
import type { StepComponentProps } from "@/modules/facilitation/components/ComponentRouter";
import type { components } from "@/shared/types/api.d.ts";
import { useClusters, submitColumnResponse, deleteResponse } from "@/modules/facilitation/hooks/api/useColumnBoard";

type ColumnResponseDto = components["schemas"]["ColumnResponseDto"];
type ClusterGroupsDto = components["schemas"]["ClusterGroupsDto"];

interface ColumnConfig {
  id: string;
  title: string;
  emoji?: string;
  color?: string;
}

interface ESVPConfig {
  columns: ColumnConfig[];
  capabilities?: {
    allowInput?: boolean;
    showContent?: boolean;
  };
}

function allResponses(clusters: ClusterGroupsDto): ColumnResponseDto[] {
  const result: ColumnResponseDto[] = [];
  const clustered = clusters.clustered ?? {};
  for (const notes of Object.values(clustered) as ColumnResponseDto[][]) {
    result.push(...notes);
  }
  result.push(...(clusters.unclustered ?? []));
  return result;
}

function InputMode({
  retroId,
  stepId,
  columns,
  responses,
  onRefresh,
}: {
  retroId: string;
  stepId: number;
  columns: ColumnConfig[];
  responses: ColumnResponseDto[];
  onRefresh: () => void;
}) {
  const [submitting, setSubmitting] = useState(false);

  const myResponse = responses[0] ?? null;
  const selectedColumnId = myResponse?.columnId ?? null;

  const handleSelect = async (columnId: string) => {
    if (submitting) return;
    setSubmitting(true);
    try {
      if (selectedColumnId === columnId) {
        // Deselect: delete existing response
        if (myResponse?.id) {
          await deleteResponse(retroId, myResponse.id);
        }
      } else {
        // Switch selection: delete old first, then submit new
        if (myResponse?.id) {
          await deleteResponse(retroId, myResponse.id);
        }
        await submitColumnResponse(retroId, stepId, columnId, columnId);
      }
      onRefresh();
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="flex flex-col items-center gap-6 py-4">
      <p className="text-sm text-gray-500 text-center max-w-md">
        Select the category that best describes how you feel about being here today.
        Your choice is anonymous — be honest!
      </p>

      <div className="grid grid-cols-2 gap-4 w-full max-w-lg">
        {columns.map((col) => {
          const isSelected = selectedColumnId === col.id;
          return (
            <button
              key={col.id}
              data-testid={`esvp-option-${col.id}`}
              onClick={() => void handleSelect(col.id)}
              disabled={submitting}
              className={[
                "flex flex-col items-center gap-2 p-5 rounded-2xl border-2 font-semibold transition-all cursor-pointer",
                "hover:scale-105 active:scale-95 disabled:opacity-50 disabled:cursor-not-allowed",
                isSelected
                  ? "border-transparent text-white shadow-lg scale-105"
                  : "border-gray-200 bg-white text-gray-700 hover:border-gray-300 hover:shadow-md",
              ].join(" ")}
              style={isSelected ? { backgroundColor: col.color ?? "#6b7280" } : {}}
            >
              <span className="text-4xl">{col.emoji ?? "❓"}</span>
              <span className="text-base">{col.title}</span>
              {isSelected && (
                <span className="text-xs font-normal opacity-80">(tap to deselect)</span>
              )}
            </button>
          );
        })}
      </div>

      {selectedColumnId ? (
        <p className="text-sm text-green-600 font-medium">
          ✓ You selected: {columns.find((c) => c.id === selectedColumnId)?.emoji}{" "}
          {columns.find((c) => c.id === selectedColumnId)?.title}
        </p>
      ) : (
        <p className="text-sm text-amber-600 font-medium">
          Please select one option above.
        </p>
      )}
    </div>
  );
}

function RevealMode({
  columns,
  responses,
}: {
  columns: ColumnConfig[];
  responses: ColumnResponseDto[];
}) {
  const total = responses.length;

  return (
    <div className="flex flex-col gap-4 py-4 max-w-lg mx-auto w-full">
      <p className="text-sm text-gray-500 text-center">
        {total === 0
          ? "No responses yet."
          : `${total} participant${total === 1 ? "" : "s"} responded`}
      </p>

      {columns.map((col) => {
        const count = responses.filter((r) => r.columnId === col.id).length;
        const pct = total > 0 ? Math.round((count / total) * 100) : 0;

        return (
          <div key={col.id} className="flex flex-col gap-1" data-testid={`esvp-result-${col.id}`}>
            <div className="flex items-center justify-between text-sm font-medium text-gray-700">
              <span>
                {col.emoji} {col.title}
              </span>
              <span className="text-gray-500">
                {count} ({pct}%)
              </span>
            </div>
            <div className="h-6 rounded-full bg-gray-100 overflow-hidden">
              <div
                className="h-full rounded-full transition-all duration-500"
                style={{
                  width: `${pct}%`,
                  backgroundColor: col.color ?? "#6b7280",
                  minWidth: count > 0 ? "1.5rem" : "0",
                }}
              />
            </div>
          </div>
        );
      })}
    </div>
  );
}

export function ESVPSelector({ retroId, stepId, componentConfig }: StepComponentProps) {
  const config = componentConfig as unknown as ESVPConfig;
  const columns = config.columns ?? [];
  const caps = config.capabilities ?? {};
  const allowInput = caps.allowInput !== false;
  const showContent = caps.showContent === true;

  const { data: clusters, isLoading, invalidate } = useClusters(retroId, stepId);

  const invalidateLocal = useCallback(() => {
    invalidate();
  }, [invalidate]);

  useSSESubscription(EventType.NOTE_ADDED, invalidateLocal);
  useSSESubscription(EventType.NOTE_UPDATED, invalidateLocal);
  useSSESubscription(EventType.NOTE_DELETED, invalidateLocal);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-40">
        <div className="w-8 h-8 rounded-full border-4 border-amber-200 border-t-amber-500 animate-spin" />
      </div>
    );
  }

  const responses = clusters ? allResponses(clusters) : [];

  if (allowInput && !showContent) {
    return (
      <InputMode
        retroId={retroId}
        stepId={stepId}
        columns={columns}
        responses={responses}
        onRefresh={invalidate}
      />
    );
  }

  return <RevealMode columns={columns} responses={responses} />;
}
