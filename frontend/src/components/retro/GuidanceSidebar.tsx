import { useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

interface GuidanceSidebarProps {
  guidance?: string | null | undefined;
  stepTitle?: string;
}

function GuidanceContent({ text }: { text: string }) {
  const lines = text.split("\n").filter((line) => line.trim().length > 0);

  return (
    <div className="space-y-2 text-sm leading-relaxed">
      {lines.map((line, idx) => {
        const trimmed = line.trim();
        const isBullet = trimmed.startsWith("-") || trimmed.startsWith("•") || trimmed.startsWith("*");
        if (isBullet) {
          const content = trimmed.replace(/^[-•*]\s*/, "");
          return (
            <div key={idx} className="flex items-start gap-2">
              <span className="mt-0.5 shrink-0 text-amber-400 text-xs">▸</span>
              <span className="text-gray-200">{content}</span>
            </div>
          );
        }
        return (
          <p key={idx} className="text-gray-300">
            {trimmed}
          </p>
        );
      })}
    </div>
  );
}

export function GuidanceSidebar({ guidance, stepTitle }: GuidanceSidebarProps) {
  const [isExpanded, setIsExpanded] = useState(true);

  const hasGuidance = guidance != null && guidance.trim().length > 0;
  const titleText = stepTitle ?? "Facilitator Guidance";

  return (
    <Card className="rounded-none border-0 border-b border-gray-700 bg-gray-800 text-white shadow-none gap-0 py-0">
      <CardHeader className="px-4 py-3 border-b border-gray-700 [.border-b]:pb-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2 min-w-0">
            <span className="text-amber-400 text-base shrink-0" aria-hidden="true">💡</span>
            <CardTitle className="text-white text-sm truncate">
              {titleText}
            </CardTitle>
          </div>
          <button
            type="button"
            onClick={() => setIsExpanded((prev) => !prev)}
            className="shrink-0 ml-2 text-gray-400 hover:text-white transition-colors rounded p-0.5"
            aria-expanded={isExpanded}
            aria-label={isExpanded ? "Collapse guidance" : "Expand guidance"}
          >
            <svg
              className={`w-4 h-4 transition-transform duration-200 ${isExpanded ? "rotate-0" : "-rotate-90"}`}
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
              strokeWidth={2}
              aria-hidden="true"
            >
              <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
            </svg>
          </button>
        </div>

        {!isExpanded && (
          <button
            type="button"
            onClick={() => setIsExpanded(true)}
            className="mt-1 text-xs text-amber-400 hover:text-amber-300 transition-colors text-left"
          >
            Show guidance ↓
          </button>
        )}
      </CardHeader>

      {isExpanded && (
        <CardContent className="px-4 py-3">
          {hasGuidance ? (
            <GuidanceContent text={guidance} />
          ) : (
            <p className="text-gray-500 text-sm italic">
              No guidance available for this step.
            </p>
          )}

          {/* Future: video/audio guidance slot */}
          <div className="mt-3" />
        </CardContent>
      )}
    </Card>
  );
}
