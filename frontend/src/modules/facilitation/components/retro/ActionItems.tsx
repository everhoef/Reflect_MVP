import { MultiColumnBoard } from "@/modules/facilitation/components/retro/MultiColumnBoard";
import type { StepComponentProps } from "@/modules/facilitation/components/ComponentRouter";

export function ActionItems(props: StepComponentProps) {
  return <MultiColumnBoard {...props} />;
}
