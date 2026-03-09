import { useEffect, useRef } from "react";
import { EventType } from "@/types/events";
import type { RetroSseEvent } from "@/types/events";

export function useSSE(
  retroId: string | undefined,
  handlers: Partial<Record<EventType, (event: RetroSseEvent) => void>>
): void {
  const handlersRef = useRef(handlers);
  handlersRef.current = handlers;

  useEffect(() => {
    if (!retroId) return;

    const source = new EventSource(`/api/retro/${retroId}/events`);

    const namedListeners: Array<{ type: string; listener: (e: MessageEvent) => void }> = [];

    for (const eventType of Object.values(EventType)) {
      const listener = (e: MessageEvent) => {
        const handler = handlersRef.current[eventType as EventType];
        if (!handler) return;
        try {
          const parsed = JSON.parse(e.data) as RetroSseEvent;
          handler(parsed);
        } catch {
          // ignore malformed
        }
      };
      source.addEventListener(eventType, listener);
      namedListeners.push({ type: eventType, listener });
    }

    source.onmessage = (e: MessageEvent) => {
      try {
        const parsed = JSON.parse(e.data) as RetroSseEvent;
        const handler = handlersRef.current[parsed.type];
        if (handler) handler(parsed);
      } catch {
        // ignore malformed
      }
    };

    return () => {
      for (const { type, listener } of namedListeners) {
        source.removeEventListener(type, listener);
      }
      source.onmessage = null;
      source.close();
    };
  }, [retroId]);
}
