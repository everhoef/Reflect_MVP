import { useCallback } from "react";
import { useParams } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useSSE } from "@/hooks/useSSE";
import { EventType } from "@/types/events";
import { ComponentRouter } from "@/components/ComponentRouter";
import { GuidanceSidebar } from "@/components/retro/GuidanceSidebar";
import { TimerCountdown } from "@/components/retro/TimerCountdown";

interface StepSummaryDto {
  id: number;
  title: string;
  componentType: string;
  advancementTrigger: string;
  durationSeconds: number | null;
  componentConfig: Record<string, unknown>;
  guidance?: string | null;
}

interface RetroStateDto {
  retroId: string;
  phase: string;
  currentStepId: number | null;
  currentStepIndex: number;
  steps: StepSummaryDto[];
  facilitatorId: string;
  isFacilitator: boolean;
  participantCount: number;
}

interface ParticipantDto {
  participantId: string;
  displayName: string;
  role: string;
}

async function fetchRetroState(retroId: string): Promise<RetroStateDto> {
  const res = await fetch(`/api/retro/${retroId}/state`);
  if (!res.ok) throw new Error(`Failed to fetch retro state: ${res.status}`);
  return res.json() as Promise<RetroStateDto>;
}

async function fetchParticipants(retroId: string): Promise<ParticipantDto[]> {
  const res = await fetch(`/api/retro/${retroId}/participants`);
  if (!res.ok) throw new Error(`Failed to fetch participants: ${res.status}`);
  return res.json() as Promise<ParticipantDto[]>;
}

async function postNextStep(retroId: string): Promise<void> {
  const raw = document.cookie
    .split("; ")
    .find((row) => row.startsWith("XSRF-TOKEN="))
    ?.split("=")[1];
  const csrfToken = raw ? decodeURIComponent(raw) : undefined;
  await fetch(`/api/retro/${retroId}/next`, {
    method: "POST",
    headers: csrfToken ? { "X-XSRF-TOKEN": csrfToken } : {},
  });
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

function StageProgressBar({ steps, currentStepIndex }: { steps: StepSummaryDto[]; currentStepIndex: number }) {
  const STAGE_LABELS = [
    "Set the Stage",
    "Gather Data",
    "Generate Insights",
    "Decide Actions",
    "Close Retro",
  ];
  const stepsPerStage = Math.max(1, Math.ceil(steps.length / 5));
  const currentStage = Math.floor(currentStepIndex / stepsPerStage);

  return (
    <nav className="flex items-center gap-1" aria-label="Retrospective stages">
      {STAGE_LABELS.map((label, idx) => {
        const isDone = idx < currentStage;
        const isActive = idx === currentStage;
        return (
          <div key={label} className="flex items-center">
            {idx > 0 && (
              <div
                className={`w-6 h-px mx-1 ${isDone ? "bg-amber-400" : "bg-gray-200"}`}
              />
            )}
            <div
              className={`flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium transition-all ${
                isDone
                  ? "bg-amber-100 text-amber-700"
                  : isActive
                  ? "bg-amber-500 text-white shadow-sm"
                  : "bg-gray-100 text-gray-400"
              }`}
              aria-current={isActive ? "step" : undefined}
            >
              {isDone && (
                <svg className="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                </svg>
              )}
              {isActive && <div className="w-1.5 h-1.5 rounded-full bg-white opacity-80" />}
              <span className="hidden sm:inline">{label}</span>
            </div>
          </div>
        );
      })}
    </nav>
  );
}

export default function RetroPage() {
  const { retroId } = useParams<{ retroId: string }>();
  const queryClient = useQueryClient();

  const { data: state, isLoading, error } = useQuery<RetroStateDto>({
    queryKey: ["retroState", retroId],
    queryFn: () => fetchRetroState(retroId!),
    enabled: !!retroId,
  });

  const { data: participants = [] } = useQuery<ParticipantDto[]>({
    queryKey: ["participants", retroId],
    queryFn: () => fetchParticipants(retroId!),
    enabled: !!retroId,
  });

  const refreshState = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: ["retroState", retroId] });
  }, [queryClient, retroId]);

  const refreshParticipants = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: ["participants", retroId] });
  }, [queryClient, retroId]);

  useSSE(retroId, {
    [EventType.STEP_ADVANCED]: refreshState,
    [EventType.SESSION_STARTED]: refreshState,
    [EventType.PHASE_STARTED]: refreshState,
    [EventType.PARTICIPANT_JOINED]: refreshParticipants,
    [EventType.PARTICIPANT_LEFT]: refreshParticipants,
  });

  const handleNext = () => {
    if (!retroId) return;
    void postNextStep(retroId).then(() => {
      refreshState();
    });
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

  if (error || !state) {
    return (
      <div className="flex items-center justify-center h-[calc(100vh-3.5rem)]">
        <div className="text-center">
          <p className="text-red-600 font-semibold">Could not load retrospective</p>
          <p className="text-gray-500 text-sm mt-1">{String(error ?? "Unknown error")}</p>
        </div>
      </div>
    );
  }

  const currentStep = state.steps.find((s) => s.id === state.currentStepId) ?? null;
  const respondedCount = 0;
  const canAdvance = respondedCount >= state.participantCount;

  return (
    <div
      className="flex flex-col h-[calc(100vh-3.5rem)]"
      data-testid="retro-content"
      data-step-index={state.currentStepIndex}
      data-phase={state.phase}
    >

      {currentStep && (
        <div className="bg-white border-b border-amber-100 px-6 py-3 flex items-center justify-between gap-4">
          <div className="flex-1 flex justify-center">
            <StageProgressBar steps={state.steps} currentStepIndex={state.currentStepIndex} />
          </div>
          {currentStep.durationSeconds != null && currentStep.durationSeconds > 0 && (
            <div className="shrink-0">
              <TimerCountdown durationSeconds={currentStep.durationSeconds} />
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
              guidance={currentStep?.guidance}
              stepTitle={currentStep?.title}
            />

            {!currentStep && (
              <div className="text-center text-gray-400 py-8 px-4">
                <p className="text-sm">Waiting for retrospective to start…</p>
              </div>
            )}
          </div>

          {state.isFacilitator && currentStep && (
            <div className="bg-gray-800 px-4 py-3 border-t border-gray-700">
              <p className="text-yellow-300 text-xs">💡 Click <strong>Next</strong> when ready to advance</p>
            </div>
          )}
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
                {state.isFacilitator && !canAdvance && (
                  <div className="flex items-center gap-2 text-sm text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-4 py-2">
                    <svg className="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                        d="M12 9v2m0 4h.01M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z" />
                    </svg>
                    <span>
                      {respondedCount} of {state.participantCount} participants responded. You can still proceed.
                    </span>
                  </div>
                )}

                {state.isFacilitator && (
                  <button
                    data-testid="next-step-button"
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
                    {state.isFacilitator && (
                      <button
                        data-testid="start-retro-button"
                        onClick={() => {
                          const csrfToken = document.cookie
                            .split("; ")
                            .find((row) => row.startsWith("XSRF-TOKEN="))
                            ?.split("=")[1];
                          void fetch(`/api/retro/${retroId}/start`, {
                            method: "POST",
                            headers: csrfToken ? { "X-XSRF-TOKEN": csrfToken } : {},
                          }).then(refreshState);
                        }}
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
                    <InitialsAvatar name={p.displayName} />
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
    </div>
  );
}
