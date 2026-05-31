import type { ComponentType as ReactComponentType } from "react";
import { MultiColumnBoard } from "@/modules/facilitation/components/retro/MultiColumnBoard";
import { RatingScale } from "@/modules/facilitation/components/retro/RatingScale";
import { HistogramChart } from "@/modules/facilitation/components/retro/HistogramChart";
import { SmartActionBuilder } from "@/modules/facilitation/components/retro/SmartActionBuilder";
import { ActionReview } from "@/modules/facilitation/components/retro/ActionReview";

export type RetroComponentType =
  | "MULTI_COLUMN_BOARD"
  | "RATING_SCALE"
  | "HISTOGRAM_CHART"
  | "GUIDANCE_MESSAGE"
  | "VISUAL_LAYOUT"
  | "SMART_ACTION_BUILDER"
  | "ACTION_REVIEW";

export interface StepComponentProps {
  retroId: string;
  stepId: number;
  componentConfig: Record<string, unknown>;
}

function GuidanceMessagePlaceholder({ stepId }: StepComponentProps) {
  return (
    <div className="flex flex-col items-center justify-center h-64 rounded-xl border-2 border-dashed border-purple-300 bg-purple-50">
      <div className="text-4xl mb-3">💬</div>
      <p className="text-purple-800 font-semibold text-lg">Guidance Message</p>
      <p className="text-purple-600 text-sm mt-1">Step {stepId} — coming soon</p>
    </div>
  );
}

function VisualLayoutPlaceholder({ stepId }: StepComponentProps) {
  return (
    <div className="flex flex-col items-center justify-center h-64 rounded-xl border-2 border-dashed border-teal-300 bg-teal-50">
      <div className="text-4xl mb-3">🖼️</div>
      <p className="text-teal-800 font-semibold text-lg">Visual Layout</p>
      <p className="text-teal-600 text-sm mt-1">Step {stepId} — coming soon</p>
    </div>
  );
}

function UnknownComponentPlaceholder({ componentConfig }: StepComponentProps) {
  const type = (componentConfig["componentType"] as string | undefined) ?? "Unknown";
  return (
    <div className="flex flex-col items-center justify-center h-64 rounded-xl border-2 border-dashed border-red-300 bg-red-50">
      <div className="text-4xl mb-3">❓</div>
      <p className="text-red-800 font-semibold text-lg">Unknown Component</p>
      <p className="text-red-600 text-sm mt-1">{type}</p>
    </div>
  );
}

const COMPONENT_REGISTRY: Record<
  RetroComponentType,
  ReactComponentType<StepComponentProps>
> = {
  MULTI_COLUMN_BOARD: MultiColumnBoard,
  RATING_SCALE: RatingScale,
  HISTOGRAM_CHART: HistogramChart,
  GUIDANCE_MESSAGE: GuidanceMessagePlaceholder,
  VISUAL_LAYOUT: VisualLayoutPlaceholder,
  SMART_ACTION_BUILDER: SmartActionBuilder,
  ACTION_REVIEW: ActionReview,
};

interface ComponentRouterProps {
  componentType: string;
  retroId: string;
  stepId: number;
  componentConfig: Record<string, unknown>;
}

export function ComponentRouter({
  componentType,
  retroId,
  stepId,
  componentConfig,
}: ComponentRouterProps) {
  const StepComponent =
    COMPONENT_REGISTRY[componentType as RetroComponentType] ??
    UnknownComponentPlaceholder;

  return (
    <StepComponent
      retroId={retroId}
      stepId={stepId}
      componentConfig={componentConfig}
    />
  );
}
