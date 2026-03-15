import { useEffect, useState } from "react";
import { useTimerState } from "@/hooks/useRetroState";

interface TimerCountdownProps {
  durationSeconds: number;
}

function formatTime(totalSeconds: number): string {
  const clamped = Math.max(0, totalSeconds);
  const mins = Math.floor(clamped / 60);
  const secs = clamped % 60;
  return `${String(mins).padStart(2, "0")}:${String(secs).padStart(2, "0")}`;
}

function colorClasses(remaining: number, total: number): string {
  if (total <= 0) return "text-gray-500 border-gray-300";
  const ratio = remaining / total;
  if (ratio > 0.5) return "text-green-600 border-green-500";
  if (ratio > 0.25) return "text-yellow-600 border-yellow-500";
  return "text-red-600 border-red-500 animate-pulse";
}

export function TimerCountdown({ durationSeconds }: TimerCountdownProps) {
  const { remainingSeconds, isPaused } = useTimerState();
  const [remaining, setRemaining] = useState<number>(durationSeconds);

  useEffect(() => {
    if (remainingSeconds === null || isPaused) {
      return;
    }

    setRemaining(remainingSeconds);

    const id = setInterval(() => {
      setRemaining((prev) => {
        const next = Math.max(0, prev - 1);
        if (next <= 0) clearInterval(id);
        return next;
      });
    }, 1000);

    return () => clearInterval(id);
  }, [remainingSeconds, isPaused]);

  if (remainingSeconds === null) {
    return (
      <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg border border-gray-200 bg-gray-50 text-gray-400 text-sm font-mono">
        <ClockIcon />
        <span>Timer not started</span>
      </div>
    );
  }

  if (isPaused) {
    return (
      <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg border border-yellow-400 bg-yellow-50 text-yellow-700 text-sm font-mono">
        <ClockIcon />
        <span>Paused — {formatTime(remaining)}</span>
      </div>
    );
  }

  if (remaining <= 0) {
    return (
      <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg border border-red-500 bg-red-50 text-red-600 text-sm font-mono font-bold animate-pulse">
        <ClockIcon />
        <span>{"Time's up!"}</span>
      </div>
    );
  }

  const classes = colorClasses(remaining, durationSeconds);

  return (
    <div className={`flex items-center gap-2 px-3 py-1.5 rounded-lg border bg-white text-sm font-mono font-semibold tabular-nums ${classes}`}>
      <ClockIcon />
      <span>{formatTime(remaining)}</span>
    </div>
  );
}

function ClockIcon() {
  return (
    <svg className="w-4 h-4 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={2}
        d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"
      />
    </svg>
  );
}
