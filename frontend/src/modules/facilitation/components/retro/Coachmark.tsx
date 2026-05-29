import { useEffect, useRef, useState, type ReactNode } from "react";

export type CoachmarkAnchorId =
  | "guidance-sidebar"
  | "next-step"
  | "timer-display"
  | "note-input"
  | "rating-submit";

export interface CoachmarkPosition {
  top: number;
  left: number;
  placement: "right" | "left" | "below" | "above";
}

function resolveAnchorPosition(
  anchorId: CoachmarkAnchorId,
  placement: CoachmarkPosition["placement"] = "right"
): CoachmarkPosition | null {
  const el = document.querySelector<HTMLElement>(
    `[data-coachmark="${anchorId}"]`
  );
  if (!el) return null;

  const rect = el.getBoundingClientRect();
  const GAP = 10;

  switch (placement) {
    case "right":
      return { top: rect.top, left: rect.right + GAP, placement };
    case "left":
      return { top: rect.top, left: rect.left - GAP, placement };
    case "below":
      return { top: rect.bottom + GAP, left: rect.left, placement };
    case "above":
      return { top: rect.top - GAP, left: rect.left, placement };
    default:
      return { top: rect.top, left: rect.right + GAP, placement: "right" };
  }
}

interface CoachmarkProps {
  anchorId: CoachmarkAnchorId;
  placement?: CoachmarkPosition["placement"];
  children: ReactNode;
  label?: string;
  onDismiss: () => void;
  "data-testid"?: string;
}

export function Coachmark({
  anchorId,
  placement = "right",
  children,
  label = "Tip",
  onDismiss,
  "data-testid": testId = "coachmark",
}: CoachmarkProps) {
  const [position, setPosition] = useState<CoachmarkPosition | null>(null);
  const [visible, setVisible] = useState(true);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function update() {
      setPosition(resolveAnchorPosition(anchorId, placement));
    }

    update();

    window.addEventListener("resize", update);
    window.addEventListener("scroll", update, true);

    return () => {
      window.removeEventListener("resize", update);
      window.removeEventListener("scroll", update, true);
    };
  }, [anchorId, placement]);

  useEffect(() => {
    if (!visible) return;

    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") {
        setVisible(false);
        onDismiss();
      }
    }

    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [visible, onDismiss]);

  function handleDismiss() {
    setVisible(false);
    onDismiss();
  }

  if (!visible) return null;
  if (position === null) return null;

  const placementStyles: Record<CoachmarkPosition["placement"], string> = {
    right: "translate-y-0",
    left: "-translate-x-full",
    below: "translate-y-0",
    above: "-translate-y-full",
  };

  const positionStyle = {
    position: "fixed" as const,
    top: position.top,
    left: position.left,
    zIndex: 9000,
  };

  return (
    <div
      ref={containerRef}
      data-testid={testId}
      data-coachmark-popup={anchorId}
      style={positionStyle}
      className={`max-w-xs pointer-events-none ${placementStyles[position.placement]}`}
      role="note"
      aria-label={`Coachmark: ${label}`}
    >
      <div className="pointer-events-none rounded-lg bg-gray-800/95 border border-amber-500/30 shadow-lg shadow-black/30 px-3.5 py-3 text-sm backdrop-blur-sm">
        <div className="flex items-center justify-between mb-1.5 gap-3">
          <span className="text-[9px] font-semibold uppercase tracking-widest text-amber-400/80">
            {label}
          </span>
          <button
            type="button"
            data-testid={`${testId}-close`}
            onClick={handleDismiss}
            className="pointer-events-auto shrink-0 text-gray-500 hover:text-gray-200 transition-colors leading-none text-[11px]"
            aria-label="Dismiss coachmark"
          >
            ✕
          </button>
        </div>

        <div
          data-testid={`${testId}-content`}
          className="text-gray-200 text-[12px] leading-relaxed"
        >
          {children}
        </div>

        {position.placement === "right" && (
          <div
            aria-hidden="true"
            className="absolute top-3 -left-1.5 w-2 h-2 rotate-45 bg-gray-800/95 border-l border-b border-amber-500/30"
          />
        )}
        {position.placement === "left" && (
          <div
            aria-hidden="true"
            className="absolute top-3 -right-1.5 w-2 h-2 rotate-45 bg-gray-800/95 border-r border-t border-amber-500/30"
          />
        )}
        {position.placement === "below" && (
          <div
            aria-hidden="true"
            className="absolute -top-1.5 left-3 w-2 h-2 rotate-45 bg-gray-800/95 border-l border-t border-amber-500/30"
          />
        )}
        {position.placement === "above" && (
          <div
            aria-hidden="true"
            className="absolute -bottom-1.5 left-3 w-2 h-2 rotate-45 bg-gray-800/95 border-r border-b border-amber-500/30"
          />
        )}
      </div>
    </div>
  );
}
