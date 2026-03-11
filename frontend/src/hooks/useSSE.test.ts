import { renderHook } from '@testing-library/react'
import { act } from '@testing-library/react'
import { useSSE } from './useSSE'
import { EventType } from '@/types/events'

type NamedListener = (e: MessageEvent) => void

class MockEventSource {
  static instances: MockEventSource[] = []

  url: string
  onopen: (() => void) | null = null
  onerror: ((e: Event) => void) | null = null

  private namedListeners: Map<string, NamedListener[]> = new Map()

  constructor(url: string) {
    this.url = url
    MockEventSource.instances.push(this)
  }

  addEventListener(type: string, listener: NamedListener) {
    if (!this.namedListeners.has(type)) {
      this.namedListeners.set(type, [])
    }
    this.namedListeners.get(type)!.push(listener)
  }

  removeEventListener(type: string, listener: NamedListener) {
    const list = this.namedListeners.get(type)
    if (!list) return
    const idx = list.indexOf(listener)
    if (idx !== -1) list.splice(idx, 1)
  }

  close = vi.fn()

  triggerOpen() {
    this.onopen?.()
  }

  triggerEvent(type: string, data: string) {
    const listeners = this.namedListeners.get(type) ?? []
    const event = new MessageEvent(type, { data })
    for (const listener of listeners) {
      listener(event)
    }
  }

  getListenerCount(type: string): number {
    return (this.namedListeners.get(type) ?? []).length
  }
}

let OriginalEventSource: typeof EventSource

beforeAll(() => {
  OriginalEventSource = globalThis.EventSource
})

beforeEach(() => {
  MockEventSource.instances = []
  // Cast: mock intentionally omits rarely-used EventSource members (CONNECTING/OPEN/CLOSED etc.)
  globalThis.EventSource = MockEventSource as unknown as typeof EventSource
})

afterAll(() => {
  globalThis.EventSource = OriginalEventSource
})

function latestInstance(): MockEventSource {
  const instance = MockEventSource.instances.at(-1)
  if (instance === undefined) throw new Error('No MockEventSource instances exist')
  return instance
}

describe('useSSE', () => {
  it('does not create an EventSource when retroId is undefined', () => {
    renderHook(() => useSSE(undefined, {}))
    expect(MockEventSource.instances).toHaveLength(0)
  })

  it('creates an EventSource with the correct URL when retroId is present', () => {
    renderHook(() => useSSE('retro-123', {}))
    expect(MockEventSource.instances).toHaveLength(1)
    expect(latestInstance().url).toBe('/api/retro/retro-123/events')
  })

  it('registers listeners for all EventType values', () => {
    renderHook(() => useSSE('retro-123', {}))
    const instance = latestInstance()
    for (const eventType of Object.values(EventType)) {
      expect(instance.getListenerCount(eventType)).toBe(1)
    }
  })

  it('calls the matching handler with raw payload when a named event is emitted', () => {
    const handler = vi.fn()
    renderHook(() =>
      useSSE('retro-123', { [EventType.NOTE_ADDED]: handler })
    )
    act(() => {
      latestInstance().triggerEvent(EventType.NOTE_ADDED, '{"responseId":"abc"}')
    })
    expect(handler).toHaveBeenCalledOnce()
    expect(handler).toHaveBeenCalledWith('{"responseId":"abc"}')
  })

  it('calls onConnected when onopen fires', () => {
    const onConnected = vi.fn()
    renderHook(() => useSSE('retro-123', {}, onConnected))
    act(() => {
      latestInstance().triggerOpen()
    })
    expect(onConnected).toHaveBeenCalledOnce()
  })

  it('calls close() on unmount', () => {
    const { unmount } = renderHook(() => useSSE('retro-123', {}))
    const instance = latestInstance()
    unmount()
    expect(instance.close).toHaveBeenCalledOnce()
  })

  it('closes the old EventSource and opens a new one when retroId changes', () => {
    const { rerender } = renderHook(
      ({ retroId }: { retroId: string }) => useSSE(retroId, {}),
      { initialProps: { retroId: 'retro-1' } }
    )
    const first = latestInstance()
    expect(first.url).toBe('/api/retro/retro-1/events')

    rerender({ retroId: 'retro-2' })

    expect(first.close).toHaveBeenCalledOnce()
    expect(MockEventSource.instances).toHaveLength(2)
    expect(latestInstance().url).toBe('/api/retro/retro-2/events')
  })

  it('uses the latest handler after a rerender (no stale closure)', () => {
    const firstHandler = vi.fn()
    const secondHandler = vi.fn()

    const { rerender } = renderHook(
      ({ handler }: { handler: (d: string) => void }) =>
        useSSE('retro-123', { [EventType.STEP_ADVANCED]: handler }),
      { initialProps: { handler: firstHandler } }
    )

    rerender({ handler: secondHandler })

    act(() => {
      latestInstance().triggerEvent(EventType.STEP_ADVANCED, 'null')
    })

    expect(firstHandler).not.toHaveBeenCalled()
    expect(secondHandler).toHaveBeenCalledOnce()
    expect(secondHandler).toHaveBeenCalledWith('null')
  })
})
