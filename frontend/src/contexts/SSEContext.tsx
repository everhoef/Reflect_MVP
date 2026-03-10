import {
  createContext,
  useRef,
  useCallback,
  type ReactNode,
} from "react";
import { EventType } from "@/types/events";

type SSEHandler = (rawData: string) => void;

export interface SSEContextValue {
  subscribe: (eventType: EventType, handler: SSEHandler) => () => void;
  dispatch: (eventType: EventType, rawData: string) => void;
}

export const SSEContext = createContext<SSEContextValue | null>(null); // eslint-disable-line react-refresh/only-export-components

export function SSEProvider({ children }: { children: ReactNode }) {
  const listenersRef = useRef<Map<EventType, Set<SSEHandler>>>(new Map());

  const subscribe = useCallback(
    (eventType: EventType, handler: SSEHandler): (() => void) => {
      let listeners = listenersRef.current.get(eventType);
      if (!listeners) {
        listeners = new Set();
        listenersRef.current.set(eventType, listeners);
      }
      listeners.add(handler);
      return () => {
        listenersRef.current.get(eventType)?.delete(handler);
      };
    },
    []
  );

  const dispatch = useCallback((eventType: EventType, rawData: string) => {
    const handlers = listenersRef.current.get(eventType);
    if (handlers) {
      for (const handler of handlers) {
        try {
          handler(rawData);
        } catch (err) {
          console.warn("SSE handler error for", eventType, ":", err);
        }
      }
    }
  }, []);

  return (
    <SSEContext.Provider value={{ subscribe, dispatch }}>
      {children}
    </SSEContext.Provider>
  );
}

