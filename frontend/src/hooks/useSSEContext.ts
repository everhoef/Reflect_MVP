import { useContext, useLayoutEffect, useEffect, useRef } from "react";
import { SSEContext } from "@/contexts/SSEContext";
import { EventType } from "@/types/events";

type SSEHandler = (rawData: string) => void;

export function useSSEContextDispatch(): (eventType: EventType, rawData: string) => void {
  const ctx = useContext(SSEContext);
  if (!ctx) {
    throw new Error("useSSEContextDispatch must be used within SSEProvider");
  }
  return ctx.dispatch;
}

export function useSSESubscription(
  eventType: EventType,
  handler: SSEHandler
): void {
  const ctx = useContext(SSEContext);
  const handlerRef = useRef(handler);
  useLayoutEffect(() => {
    handlerRef.current = handler;
  });

  useEffect(() => {
    if (!ctx) return;
    const stableHandler: SSEHandler = (rawData) => handlerRef.current(rawData);
    return ctx.subscribe(eventType, stableHandler);
  }, [ctx, eventType]);
}
