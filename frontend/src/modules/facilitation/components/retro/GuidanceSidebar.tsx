export interface PrivateCoachingNote {
  text: string;
}

export interface HistoryMessage {
  stepTitle: string;
  publicText: string;
}

const HISTORY_RENDER_MAX = 3;

export interface FacilitatorQuickActionsConfig {
  /** Callback for the "Advance step" quick action. When undefined, the action is not shown. */
  onAdvanceStep?: () => void;
}

interface GuidanceSidebarProps {
  guidance?: string | null | undefined;
  stepTitle?: string | undefined;
  isFacilitator?: boolean;
  privateCoachingNote?: PrivateCoachingNote | null;
  /** Previous shared messages, newest-first. Only the first 3 are rendered. */
  previousMessages?: HistoryMessage[];
  /** Compact facilitator quick actions (facilitator-only, secondary to main message). */
  quickActions?: FacilitatorQuickActionsConfig | null;
}

function MessageBody({ text }: { text: string }) {
  const lines = text.split("\n").filter((line) => line.trim().length > 0);

  return (
    <div className="space-y-2">
      {lines.map((line, idx) => {
        const trimmed = line.trim();
        const isBullet =
          trimmed.startsWith("-") ||
          trimmed.startsWith("•") ||
          trimmed.startsWith("*");
        if (isBullet) {
          const content = trimmed.replace(/^[-•*]\s*/, "");
          return (
            <div key={idx} className="flex items-start gap-2">
              <span className="mt-[4px] shrink-0 text-amber-400/90 text-[9px] leading-none">◆</span>
              <span className="text-gray-100 text-[13px] leading-relaxed">{content}</span>
            </div>
          );
        }
        return (
          <p key={idx} className="text-gray-100 text-[13px] leading-relaxed">
            {trimmed}
          </p>
        );
      })}
    </div>
  );
}

export function GuidanceSidebar({
  guidance,
  stepTitle,
  isFacilitator = false,
  privateCoachingNote,
  previousMessages = [],
  quickActions,
}: GuidanceSidebarProps) {
  const hasGuidance = guidance != null && guidance.trim().length > 0;
  const hasPrivateNote =
    isFacilitator &&
    privateCoachingNote != null &&
    privateCoachingNote.text.trim().length > 0;

  const visibleHistory = previousMessages.slice(0, HISTORY_RENDER_MAX);

  const hasQuickActions =
    isFacilitator && quickActions != null && quickActions.onAdvanceStep != null;

  return (
    <>
      <div
        data-testid="guidance-sidebar"
        data-assistant-testid="assistant-shell"
        data-coachmark="guidance-sidebar"
        className="px-3 pt-3 pb-1"
      >
        <div className="flex items-center justify-between mb-2 px-0.5">
          <span className="text-[10px] font-semibold uppercase tracking-widest text-amber-600/70 truncate">
            {stepTitle ?? "Now"}
          </span>
        </div>

        <div
          data-testid="guidance-content"
          data-assistant-testid="assistant-current-message"
          className="rounded-lg bg-gray-800/70 border-l-[3px] border-amber-500/80 px-4 py-3 shadow-sm"
        >
          {hasGuidance ? (
            <MessageBody text={guidance} />
          ) : (
            <p className="text-gray-500 text-[12px] italic leading-relaxed">
              No guidance available for this step.
            </p>
          )}
        </div>

        <div
          data-testid="assistant-history-list"
          data-assistant-testid="assistant-history-list"
          aria-hidden={visibleHistory.length === 0 ? "true" : undefined}
          className={visibleHistory.length > 0 ? "mt-4 space-y-1" : undefined}
        >
          {visibleHistory.length > 0 && (
            <p className="text-[9px] uppercase tracking-widest text-gray-600 px-0.5 mb-1.5">
              Earlier
            </p>
          )}
          {visibleHistory.map((msg, idx) => (
            <div
              key={idx}
              className="rounded border-l border-gray-700/60 bg-gray-800/25 px-3 py-1.5"
            >
              {msg.stepTitle ? (
                <p className="text-[9px] font-semibold uppercase tracking-widest text-gray-600 mb-0.5">
                  {msg.stepTitle}
                </p>
              ) : null}
              <p className="text-gray-600 text-[11px] leading-snug">{msg.publicText}</p>
            </div>
          ))}
        </div>
      </div>

      {hasPrivateNote ? (
        <div
          data-testid="assistant-private-coaching"
          data-assistant-testid="assistant-private-coaching"
          className="mx-3 mt-4"
        >
          <div className="flex items-center gap-1.5 mb-1.5 px-0.5">
            <div className="h-px flex-1 bg-gray-700/40" />
            <span className="text-[9px] font-semibold uppercase tracking-widest text-gray-600/80 shrink-0">
              Private note
            </span>
            <div className="h-px flex-1 bg-gray-700/40" />
          </div>
          <div className="rounded bg-gray-800/40 border-l border-amber-600/30 px-3 py-2">
            <p className="text-amber-200/60 text-[11px] leading-relaxed">
              {privateCoachingNote.text}
            </p>
          </div>
        </div>
      ) : (
        <div
          data-testid="assistant-private-coaching-placeholder"
          data-assistant-testid="assistant-private-coaching"
          aria-hidden="true"
          hidden
        />
      )}

      {hasQuickActions ? (
        <div
          data-testid="facilitator-quick-actions"
          data-assistant-testid="facilitator-quick-actions"
          className="mx-3 mt-3 mb-3"
        >
          <div className="flex items-center gap-1.5 mb-1.5 px-0.5">
            <div className="h-px flex-1 bg-gray-700/40" />
            <span className="text-[9px] font-semibold uppercase tracking-widest text-gray-600/80 shrink-0">
              Quick actions
            </span>
            <div className="h-px flex-1 bg-gray-700/40" />
          </div>
          <div className="flex flex-wrap gap-1.5">
            {quickActions!.onAdvanceStep != null && (
              <button
                type="button"
                data-testid="quick-action-advance-step"
                onClick={quickActions!.onAdvanceStep}
                className="px-2.5 py-1 rounded text-[11px] font-medium text-gray-500 bg-gray-800/50 border border-gray-700/50 hover:border-amber-500/60 hover:text-amber-300 transition-colors"
              >
                Advance step →
              </button>
            )}
          </div>
        </div>
      ) : (
        <div
          data-testid="facilitator-quick-actions-placeholder"
          data-assistant-testid="facilitator-quick-actions"
          aria-hidden="true"
          hidden
        />
      )}
    </>
  );
}
