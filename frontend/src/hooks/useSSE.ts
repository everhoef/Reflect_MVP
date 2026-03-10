import { useEffect, useLayoutEffect, useRef } from "react";
import { EventType } from "@/types/events";

/**
 * Handler receives the raw payload string from the SSE data field.
 *
 * The backend sends named SSE events where `data:` contains only the
 * serialized payload (e.g. `"refresh"` for null payloads, or a JSON
 * object string for data payloads) — NOT a full RetroSseEvent envelope.
 *
 * Transport reconnection is delegated to the browser's native EventSource
 * implementation. This hook only manages the React lifecycle: open on mount
 * (or retroId change), register typed event listeners, close on unmount.
 */
export function useSSE(
  retroId: string | undefined,
  handlers: Partial<Record<EventType, (rawData: string) => void>>,
  onConnected?: () => void
): void {
  const handlersRef = useRef(handlers);
  useLayoutEffect(() => {
    handlersRef.current = handlers;
  });

  const onConnectedRef = useRef(onConnected);
  useLayoutEffect(() => {
    onConnectedRef.current = onConnected;
  });

  useEffect(() => {
    if (!retroId) return;

    const source = new EventSource(`/api/retro/${retroId}/events`);

    for (const eventType of Object.values(EventType)) {
      source.addEventListener(eventType, (e: MessageEvent) => {
        const handler = handlersRef.current[eventType as EventType];
        if (!handler) return;
        try {
          handler(e.data as string);
        } catch (err) {
          console.warn("SSE handler error for event type", eventType, ":", err);
        }
      });
    }

    source.onopen = () => {
      onConnectedRef.current?.();
    };

    return () => {
      source.close();
    };
  }, [retroId]);
}
