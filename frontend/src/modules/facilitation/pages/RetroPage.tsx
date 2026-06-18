import { useCallback, useEffect, useRef, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { useSSE } from "@/shared/hooks/useSSE";
import { EventType } from "@/shared/types/events";
import { ComponentRouter } from "@/modules/facilitation/components/ComponentRouter";
import { GuidanceSidebar, type PrivateCoachingNote, type FacilitatorQuickActionsConfig } from "@/modules/facilitation/components/retro/GuidanceSidebar";
import { Coachmark } from "@/modules/facilitation/components/retro/Coachmark";
import { TimerCountdown } from "@/modules/facilitation/components/retro/TimerCountdown";
import { SSEProvider } from "@/shared/contexts/SSEContext";
import { useSSEContextDispatch } from "@/shared/hooks/useSSEContext";
import { useAppliedVersion, useParticipants, useNextStep, useRetroState, useStartSession, useTimer, usePauseTimer, useResumeTimer, fetchParticipants, fetchRetroState, fetchTimerState } from "@/modules/facilitation/hooks/api/useRetro";
import { useTimerState } from "@/modules/facilitation/hooks/useRetroState";
import { ApiError } from "@/shared/lib/api-client";
import { useRetroStateStore } from "@/modules/facilitation/store/retroStore";
import { useAssistantStore } from "@/modules/facilitation/store/assistantStore";
import type { AssistantState } from "@/shared/types/assistantState";
import type { components } from "@/shared/types/api.d.ts";
import { fetchActionItems } from "@/modules/facilitation/hooks/api/useActionItems";
import { useEscalations, fetchEscalations } from "@/modules/facilitation/hooks/api/useEscalations";

const FACILITATOR_FALLBACK_COACHING_NOTE =
  "Click Next when ready to advance the room to the next step.";

// Normalized local types with required fields (schema generates optional fields from OpenAPI)
interface StepSummaryDto {
  id: number;
  title: string;
  componentType: string;
  advancementTrigger: string;
  durationSeconds: number | null;
  componentConfig: Record<string, unknown>;
  guidance?: string | null;
}

interface RetroState {
  retroId: string;
  syncVersion: number | null;
  phase: string;
  currentStepId: number | null;
  currentStepIndex: number;
  steps: StepSummaryDto[];
  facilitatorId: string;
  isFacilitator: boolean;
  participantCount: number;
  assistantState: AssistantState | null;
}

type RetroStateDtoWithSyncVersion = components["schemas"]["RetroStateDto"] & {
  syncVersion?: number | null;
};

function normalizeStep(s: components["schemas"]["StepSummaryDto"]): StepSummaryDto {
  return {
    id: s.id ?? 0,
    title: s.title ?? "",
    componentType: s.componentType ?? "",
    advancementTrigger: s.advancementTrigger ?? "",
    durationSeconds: s.durationSeconds ?? null,
    componentConfig: (s.componentConfig as Record<string, unknown>) ?? {},
    guidance: s.guidance ?? null,
  };
}

function normalizeAssistantState(
  raw: components["schemas"]["AssistantStateDto"] | null | undefined
): AssistantState | null {
  if (raw == null) return null;
  return {
    current: raw.current
      ? {
          retroId: raw.current.retroId ?? "",
          stepId: raw.current.stepId ?? 0,
          stepTitle: raw.current.stepTitle ?? "",
          publicText: raw.current.publicText ?? "",
        }
      : null,
    history: (raw.history ?? []).map((m) => ({
      retroId: m.retroId ?? "",
      stepId: m.stepId ?? 0,
      stepTitle: m.stepTitle ?? "",
      publicText: m.publicText ?? "",
    })),
    facilitatorCoachingNote: raw.facilitatorCoachingNote ?? null,
  };
}

function normalizeState(raw: RetroStateDtoWithSyncVersion): RetroState {
  return {
    retroId: raw.retroId ?? "",
    syncVersion: raw.syncVersion ?? null,
    phase: raw.phase ?? "LOBBY",
    currentStepId: raw.currentStepId ?? null,
    currentStepIndex: raw.currentStepIndex ?? 0,
    steps: (raw.steps ?? []).map(normalizeStep),
    facilitatorId: raw.facilitatorId ?? "",
    isFacilitator: raw.isFacilitator ?? false,
    participantCount: raw.participantCount ?? 0,
    assistantState: normalizeAssistantState(raw.assistantState),
  };
}

function InitialsAvatar({ name }: { name: string }) {
  const initials = name
    .split(" ")
    .slice(0, 2)
    .map((w) => w[0]?.toUpperCase() ?? "")
    .join("");
  return (
    <div className="w-8 h-8 rounded-full flex items-center justify-center text-white text-xs font-semibold bg-blue-500 shrink-0">
      {initials}
    </div>
  );
}


export default function RetroPage() {
  return (
    <SSEProvider>
      <RetroPageInner />
    </SSEProvider>
  );
}

function RetroPageInner() {
  const { retroId } = useParams<{ retroId: string }>();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [copied, setCopied] = useState(false);
  const [showGuidanceTip, setShowGuidanceTip] = useState(true);
  const [showNextStepTip, setShowNextStepTip] = useState(true);
  const [showNoteInputTip, setShowNoteInputTip] = useState(true);
  const reconcileInFlightRef = useRef<Promise<number | null> | null>(null);
  const sseDispatch = useSSEContextDispatch();
  const appliedVersion = useAppliedVersion(retroId);

  const handleCopyRetroId = () => {
    if (!retroId) return;
    const succeed = () => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    };
    if (navigator.clipboard) {
      void navigator.clipboard.writeText(retroId).then(succeed).catch(() => {
        // fallback for non-HTTPS (e.g. local NAS over HTTP)
        const el = document.createElement("textarea");
        el.value = retroId;
        document.body.appendChild(el);
        el.select();
        document.execCommand("copy");
        document.body.removeChild(el);
        succeed();
      });
    } else {
      const el = document.createElement("textarea");
      el.value = retroId;
      document.body.appendChild(el);
      el.select();
      document.execCommand("copy");
      document.body.removeChild(el);
      succeed();
    }
  };

  const { data: rawState, isLoading, error } = useRetroState(retroId) as {
    data: RetroStateDtoWithSyncVersion | undefined;
    isLoading: boolean;
    error: unknown;
  };

  const { data: participants = [] } = useParticipants(retroId);

  const nextStepMutation = useNextStep(retroId);
  const startSessionMutation = useStartSession(retroId);

  const refreshState = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: ["retroState", retroId] });
  }, [queryClient, retroId]);

  const refreshParticipants = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: ["participants", retroId] });
  }, [queryClient, retroId]);

  const { data: timerData } = useTimer(retroId);
  useEscalations(retroId);
  const setTimerState = useRetroStateStore((s) => s.setTimerState);
  const clearTimer = useRetroStateStore((s) => s.clearTimer);
  const setCurrentPhase = useRetroStateStore((s) => s.setCurrentPhase);
  const pauseTimerMutation = usePauseTimer(retroId);
  const resumeTimerMutation = useResumeTimer(retroId);
  const { remainingSeconds, isPaused } = useTimerState();

  const assistantCurrent = useAssistantStore((s) => s.current);
  const assistantHistory = useAssistantStore((s) => s.history);
  const facilitatorCoachingNote = useAssistantStore((s) => s.facilitatorCoachingNote);
  const bootstrapAssistant = useAssistantStore((s) => s.bootstrapState);

  useEffect(() => {
    if (timerData != null) {
      setTimerState({
        remainingSeconds: timerData.remainingSeconds ?? null,
        isPaused: timerData.isPaused ?? false,
        timerState: timerData.state ?? null,
      });
    } else if (timerData === null) {
      clearTimer();
    }
  }, [timerData, setTimerState, clearTimer]);

  useEffect(() => {
    if (rawState?.phase) {
      setCurrentPhase(rawState.phase);
    }
  }, [rawState?.phase, setCurrentPhase]);

  useEffect(() => {
    const normalized = rawState ? normalizeState(rawState) : null;
    if (normalized?.assistantState != null) {
      bootstrapAssistant(normalized.assistantState);
    }
  }, [rawState, bootstrapAssistant]);

  const reconcileBundle = useCallback(() => {
    if (!retroId) {
      return Promise.resolve<number | null>(null);
    }

    if (reconcileInFlightRef.current) {
      return reconcileInFlightRef.current;
    }

    const promise = (async () => {
      try {
        const [nextState] = await Promise.all([
          queryClient.fetchQuery({
            queryKey: ["retroState", retroId],
            staleTime: 0,
            queryFn: () => fetchRetroState(retroId),
          }),
          queryClient.fetchQuery({
            queryKey: ["participants", retroId],
            staleTime: 0,
            queryFn: () => fetchParticipants(retroId),
          }),
          queryClient.fetchQuery({
            queryKey: ["timer", retroId],
            staleTime: 0,
            queryFn: () => fetchTimerState(retroId),
          }),
          queryClient.fetchQuery({
            queryKey: ["actionItems", retroId],
            staleTime: 0,
            queryFn: () => fetchActionItems(retroId),
          }),
          queryClient.fetchQuery({
            queryKey: ["escalations", retroId],
            staleTime: 0,
            queryFn: () => fetchEscalations(retroId),
          }),
        ]);

        const nextAppliedVersion = nextState.syncVersion ?? null;
        return nextAppliedVersion;
      } finally {
        reconcileInFlightRef.current = null;
      }
    })();

    reconcileInFlightRef.current = promise;
    return promise;
  }, [queryClient, retroId]);

  const { signaledVersion, connectionState, openCount } = useSSE(retroId, {
    [EventType.STEP_ADVANCED]: (data) => {
      void reconcileBundle();
      sseDispatch(EventType.STEP_ADVANCED, data);
    },
    [EventType.SESSION_STARTED]: (data) => { refreshState(); sseDispatch(EventType.SESSION_STARTED, data); },
    [EventType.PHASE_STARTED]: (data) => { refreshState(); sseDispatch(EventType.PHASE_STARTED, data); },
    [EventType.PARTICIPANT_JOINED]: (data) => { refreshParticipants(); sseDispatch(EventType.PARTICIPANT_JOINED, data); },
    [EventType.PARTICIPANT_LEFT]: (data) => { refreshParticipants(); sseDispatch(EventType.PARTICIPANT_LEFT, data); },
    [EventType.NOTE_ADDED]: (data) => sseDispatch(EventType.NOTE_ADDED, data),
    [EventType.NOTE_UPDATED]: (data) => sseDispatch(EventType.NOTE_UPDATED, data),
    [EventType.NOTE_DELETED]: (data) => sseDispatch(EventType.NOTE_DELETED, data),
    [EventType.VOTE_ADDED]: (data) => sseDispatch(EventType.VOTE_ADDED, data),
    [EventType.VOTE_REMOVED]: (data) => sseDispatch(EventType.VOTE_REMOVED, data),
    [EventType.ACTION_CREATED]: (data) => sseDispatch(EventType.ACTION_CREATED, data),
    [EventType.ACTION_UPDATED]: (data) => sseDispatch(EventType.ACTION_UPDATED, data),
    [EventType.ACTION_DELETED]: (data) => sseDispatch(EventType.ACTION_DELETED, data),
    [EventType.ESCALATION_CREATED]: (data) => sseDispatch(EventType.ESCALATION_CREATED, data),
    [EventType.ESCALATION_VOTE_UPDATED]: (data) => sseDispatch(EventType.ESCALATION_VOTE_UPDATED, data),
    [EventType.TIMER_STARTED]: () => { void queryClient.invalidateQueries({ queryKey: ["timer", retroId] }); },
    [EventType.TIMER_PAUSED]: () => { void queryClient.invalidateQueries({ queryKey: ["timer", retroId] }); },
    [EventType.TIMER_FINISHED]: () => { void queryClient.invalidateQueries({ queryKey: ["timer", retroId] }); },
  });

  useEffect(() => {
    if (!retroId || openCount === 0) {
      return;
    }

    void reconcileBundle();
  }, [openCount, reconcileBundle, retroId]);

  useEffect(() => {
    if (signaledVersion == null) {
      return;
    }

    if (appliedVersion == null || signaledVersion > appliedVersion) {
      void reconcileBundle();
    }
  }, [appliedVersion, reconcileBundle, signaledVersion]);

  const handleNext = () => {
    if (!retroId) return;
    nextStepMutation.mutate();
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-[calc(100vh-3.5rem)]">
        <div className="flex flex-col items-center gap-3">
          <div className="w-10 h-10 rounded-full border-4 border-amber-200 border-t-amber-500 animate-spin" />
          <p className="text-gray-500 text-sm">Loading retrospective…</p>
        </div>
      </div>
    );
  }

  if (error || !rawState) {
    const is404 = error instanceof ApiError && error.status === 404;
    return (
      <div className="flex items-center justify-center h-[calc(100vh-3.5rem)]">
        <div className="text-center">
          <p className="text-red-600 font-semibold text-lg">
            {is404 ? "Retrospective not found" : "Could not load retrospective"}
          </p>
          <p className="text-gray-500 text-sm mt-1">
            {is404
              ? "The session may have ended or the link is invalid."
              : String(error ?? "Unknown error")}
          </p>
          <button
            onClick={() => navigate("/")}
            className="mt-4 px-5 py-2 rounded-lg bg-amber-500 hover:bg-amber-600 text-white text-sm font-medium transition-colors"
          >
            Go Home
          </button>
        </div>
      </div>
    );
  }

  const state: RetroState = normalizeState(rawState);
  
  const currentStep = state.steps.find((s) => s.id === state.currentStepId) ?? null;

  const syncState =
    signaledVersion != null && appliedVersion != null
      ? signaledVersion > appliedVersion
        ? "reconciling"
        : "settled"
      : "unknown";

  return (
    <div
      className="flex flex-col h-[calc(100vh-3.5rem)]"
      data-testid="retro-content"
      data-step-index={state.currentStepIndex}
      data-phase={state.phase}
      data-sse-connected={connectionState === "open" ? "true" : "false"}
      data-sync-state={syncState}
    >

      {currentStep && (
        <div className="bg-white border-b border-amber-100 px-6 py-3 flex items-center justify-end gap-4">
          {currentStep.durationSeconds != null && currentStep.durationSeconds > 0 && (
            <div className="shrink-0 flex items-center gap-2">
              <TimerCountdown durationSeconds={currentStep.durationSeconds} />
              {state.isFacilitator && remainingSeconds !== null && (
                isPaused ? (
                  <button
                    data-testid="resume-timer-button"
                    onClick={() => resumeTimerMutation.mutate()}
                    className="px-3 py-1.5 text-xs font-medium bg-green-600 hover:bg-green-700 text-white rounded-md transition-colors"
                  >
                    ▶ Resume
                  </button>
                ) : (
                  <button
                    data-testid="pause-timer-button"
                    onClick={() => pauseTimerMutation.mutate()}
                    className="px-3 py-1.5 text-xs font-medium bg-yellow-600 hover:bg-yellow-700 text-white rounded-md transition-colors"
                  >
                    ⏸ Pause
                  </button>
                )
              )}
            </div>
          )}
        </div>
      )}

      <div className="flex flex-1 overflow-hidden">

        <aside className="w-72 min-w-72 shrink-0 bg-gray-900 flex flex-col overflow-hidden">
          <div className="bg-gray-800 px-4 py-3 border-b border-gray-700">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-full flex items-center justify-center text-2xl bg-[#C49A1A]">
                🤖
              </div>
              <div>
                <p className="text-white font-semibold text-sm">Virtual Facilitator</p>
                <p className="text-gray-400 text-xs">Guiding your retrospective</p>
              </div>
            </div>
          </div>

          <div className="flex-1 overflow-y-auto">
            <GuidanceSidebar
              guidance={assistantCurrent?.publicText}
              stepTitle={currentStep?.title}
              isFacilitator={state.isFacilitator}
              previousMessages={assistantHistory}
              privateCoachingNote={
                state.isFacilitator
                  ? ({ text: facilitatorCoachingNote ?? FACILITATOR_FALLBACK_COACHING_NOTE } satisfies PrivateCoachingNote)
                  : null
              }
              quickActions={
                state.isFacilitator && currentStep != null
                  ? ({ onAdvanceStep: handleNext } satisfies FacilitatorQuickActionsConfig)
                  : null
              }
            />
          </div>

        </aside>

        <main className="flex-1 bg-white overflow-y-auto">
          {currentStep ? (
            <div className="p-8">
              <div className="text-center mb-8">
                <h2 className="text-2xl font-bold text-gray-900">
                  Step {state.currentStepIndex + 1}: {currentStep.title}
                </h2>
              </div>

              <ComponentRouter
                componentType={currentStep.componentType}
                retroId={retroId!}
                stepId={currentStep.id}
                componentConfig={currentStep.componentConfig}
              />

              <div className="mt-8 flex flex-col items-center gap-3">
                {state.isFacilitator && (
                  <button
                    data-testid="next-step-button"
                    data-coachmark="next-step"
                    onClick={handleNext}
                    className="px-8 py-2.5 rounded-lg text-white font-medium transition-colors bg-[#C49A1A] hover:bg-[#a8831a]"
                  >
                    Next →
                  </button>
                )}
              </div>
            </div>
          ) : (
            <div className="flex items-center justify-center h-full">
              <div className="text-center py-12">
                {state.phase === "LOBBY" && (
                  <div>
                    <h2 className="text-xl font-semibold text-gray-800 mb-2">Session Lobby</h2>
                    <p className="text-gray-500 text-sm mb-4">Waiting for the facilitator to start the retrospective.</p>

                    <div className="mb-6 p-4 bg-gray-50 border border-gray-200 rounded-lg flex items-center justify-between gap-4">
                      <div className="flex items-center gap-2 min-w-0">
                        <span className="text-gray-500 text-sm shrink-0">Session ID:</span>
                        <span
                          data-testid="retro-id-display"
                          className="font-mono text-sm text-amber-700 truncate"
                        >
                          {retroId}
                        </span>
                      </div>
                      <button
                        data-testid="copy-retro-id-button"
                        onClick={handleCopyRetroId}
                        className="shrink-0 px-3 py-1.5 text-xs font-medium bg-amber-500 hover:bg-amber-600 text-white rounded-md transition-colors"
                      >
                        {copied ? "Copied!" : "Copy"}
                      </button>
                    </div>

                    {state.isFacilitator && (
                      <button
                        data-testid="start-retro-button"
                        onClick={() => startSessionMutation.mutate()}
                        className="px-6 py-2.5 bg-green-600 hover:bg-green-700 text-white rounded-lg font-medium transition-colors"
                      >
                        Start Retrospective
                      </button>
                    )}
                  </div>
                )}
                {state.phase === "COMPLETED" && (
                  <div>
                    <h4 className="text-xl font-semibold text-gray-800 mb-2">Retrospective Complete</h4>
                    <p className="text-gray-500 text-sm">Thank you for participating!</p>
                  </div>
                )}
                {state.phase !== "LOBBY" && state.phase !== "COMPLETED" && (
                  <div>
                    <h4 className="text-xl font-semibold text-gray-800 mb-2">No Active Step</h4>
                    <p className="text-gray-500 text-sm">Phase: {state.phase}</p>
                  </div>
                )}
              </div>
            </div>
          )}
        </main>

        <aside className="w-72 min-w-72 shrink-0 bg-gray-50 border-l border-gray-200 overflow-y-auto">
          <div className="p-5">
            <h3 className="text-sm font-semibold text-gray-700 uppercase tracking-wider mb-3">
              Participants ({(participants ?? []).length})
            </h3>
            <ul id="participants-list" className="space-y-2">
              {(participants ?? []).map((p) => (
                <li key={p.participantId} className="flex items-center justify-between p-2.5 bg-white rounded-lg border border-gray-100">
                  <span className="text-sm font-medium text-gray-800">{p.displayName}</span>
                  <div className="flex items-center gap-2">
                    <InitialsAvatar name={p.displayName ?? ""} />
                    {p.role === "FACILITATOR" && (
                      <span className="text-xs bg-amber-100 text-amber-800 px-2 py-0.5 rounded-full font-medium">
                        Facilitator
                      </span>
                    )}
                  </div>
                </li>
              ))}
              {(participants ?? []).length === 0 && (
                <li className="text-gray-400 text-sm text-center py-4">No participants yet</li>
              )}
            </ul>
          </div>
        </aside>

      </div>

      {currentStep && showGuidanceTip && (
        <Coachmark
          anchorId="guidance-sidebar"
          placement="right"
          label="Tip"
          onDismiss={() => setShowGuidanceTip(false)}
          data-testid="guidance-sidebar-coachmark"
        >
          The Virtual Facilitator guides you step by step. Dismiss when ready.
        </Coachmark>
      )}

      {currentStep && state.isFacilitator && showNextStepTip && (
        <Coachmark
          anchorId="next-step"
          placement="above"
          label="Facilitator"
          onDismiss={() => setShowNextStepTip(false)}
          data-testid="next-step-coachmark"
        >
          When the room has shared their thoughts, click Next to advance everyone to the next step.
        </Coachmark>
      )}

      {currentStep && currentStep.componentType === "MULTI_COLUMN_BOARD" && showNoteInputTip && (
        <Coachmark
          anchorId="note-input"
          placement="below"
          label="Tip"
          onDismiss={() => setShowNoteInputTip(false)}
          data-testid="note-input-coachmark"
        >
          Type your note and press Enter or click ➕ to add it to the board.
        </Coachmark>
      )}
    </div>
  );
}
