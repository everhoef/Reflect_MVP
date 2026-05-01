import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { EventType, type RetroSseSignalEnvelope } from "@/shared/types/events";

export type SseConnectionState = "idle" | "connecting" | "open";

export interface UseSSETransportState {
  signaledVersion: number | null;
  connectionState: SseConnectionState;
  openCount: number;
}

export function useSSE(
  retroId: string | undefined,
  handlers: Partial<Record<EventType, (rawData: string) => void>>,
  onConnected?: () => void
): UseSSETransportState {
  function parseEnvelope(rawData: string): RetroSseSignalEnvelope<unknown> | null {
    try {
      const parsed = JSON.parse(rawData) as RetroSseSignalEnvelope<unknown>;
      if (
        typeof parsed === "object" &&
        parsed !== null &&
        "syncVersion" in parsed &&
        "payload" in parsed
      ) {
        return parsed;
      }
    } catch {
      return null;
    }

    return null;
  }

  function unwrapEnvelope(rawData: string): string {
    const parsed = parseEnvelope(rawData);
    if (!parsed) {
      return rawData;
    }

    return parsed.payload === null ? "null" : JSON.stringify(parsed.payload);
  }

  const handlersRef = useRef(handlers);
  useLayoutEffect(() => {
    handlersRef.current = handlers;
  });

  const onConnectedRef = useRef(onConnected);
  useLayoutEffect(() => {
    onConnectedRef.current = onConnected;
  });

  const [signaledVersion, setSignaledVersion] = useState<number | null>(null);
  const [connectionState, setConnectionState] = useState<SseConnectionState>(
    retroId ? "connecting" : "idle"
  );
  const [openCount, setOpenCount] = useState(0);

  useEffect(() => {
    if (!retroId) {
      setConnectionState("idle");
      setOpenCount(0);
      setSignaledVersion(null);
      return;
    }

    setConnectionState("connecting");

    const source = new EventSource(`/api/retros/${retroId}/events`);

    for (const eventType of Object.values(EventType)) {
      source.addEventListener(eventType, (e: MessageEvent) => {
        const envelope = parseEnvelope(e.data as string);
        if (envelope && Number.isFinite(envelope.syncVersion)) {
          setSignaledVersion((current) =>
            current === null
              ? envelope.syncVersion
              : Math.max(current, envelope.syncVersion)
          );
        }

        const handler = handlersRef.current[eventType as EventType];
        if (!handler) return;
        try {
          handler(unwrapEnvelope(e.data as string));
        } catch (err) {
          console.warn("SSE handler error for event type", eventType, ":", err);
        }
      });
    }

    source.onopen = () => {
      setConnectionState("open");
      setOpenCount((count) => count + 1);
      onConnectedRef.current?.();
    };

    source.onerror = () => {
      setConnectionState("connecting");
    };

    return () => {
      source.close();
    };
  }, [retroId]);

  return {
    signaledVersion,
    connectionState,
    openCount,
  };
}
