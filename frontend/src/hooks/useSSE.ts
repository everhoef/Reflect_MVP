import { useEffect, useRef } from "react";
import { EventType } from "@/types/events";

/**
 * Handler receives the raw payload string from the SSE data field.
 *
 * The backend sends named SSE events where `data:` contains only the
 * serialized payload (e.g. `"refresh"` for null payloads, or a JSON
 * object string for data payloads) — NOT a full RetroSseEvent envelope.
 */
export function useSSE(
  retroId: string | undefined,
  handlers: Partial<Record<EventType, (rawData: string) => void>>
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
          handler(e.data as string);
        } catch (err) {
          console.warn("SSE handler error for event type", eventType, ":", err);
        }
      };
      source.addEventListener(eventType, listener);
      namedListeners.push({ type: eventType, listener });
    }

    source.onerror = (err) => {
      console.warn("SSE connection error:", err);
    };

    return () => {
      for (const { type, listener } of namedListeners) {
        source.removeEventListener(type, listener);
      }
      source.onerror = null;
      source.close();
    };
  }, [retroId]);
}
