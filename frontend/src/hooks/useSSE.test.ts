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

  triggerError() {
    this.onerror?.(new Event('error'))
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
  it('returns idle transport state when retroId is undefined', () => {
    const { result } = renderHook(() => useSSE(undefined, {}))
    expect(MockEventSource.instances).toHaveLength(0)
    expect(result.current).toEqual({
      signaledVersion: null,
      connectionState: 'idle',
      openCount: 0,
    })
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

  it('unwraps the SSE transport envelope and passes the payload raw string to the handler', () => {
    const handler = vi.fn()
    const { result } = renderHook(() =>
      useSSE('retro-123', { [EventType.NOTE_ADDED]: handler })
    )
    act(() => {
      latestInstance().triggerEvent(
        EventType.NOTE_ADDED,
        '{"syncVersion":12,"payload":{"responseId":"abc"}}'
      )
    })
    expect(handler).toHaveBeenCalledOnce()
    expect(handler).toHaveBeenCalledWith('{"responseId":"abc"}')
    expect(result.current.signaledVersion).toBe(12)
  })

  it('preserves null payloads when the SSE transport envelope is emitted', () => {
    const handler = vi.fn()
    renderHook(() =>
      useSSE('retro-123', { [EventType.STEP_ADVANCED]: handler })
    )

    act(() => {
      latestInstance().triggerEvent(
        EventType.STEP_ADVANCED,
        '{"syncVersion":15,"payload":null}'
      )
    })

    expect(handler).toHaveBeenCalledOnce()
    expect(handler).toHaveBeenCalledWith('null')
  })

  it('calls onConnected when onopen fires', () => {
    const onConnected = vi.fn()
    const { result } = renderHook(() => useSSE('retro-123', {}, onConnected))
    act(() => {
      latestInstance().triggerOpen()
    })
    expect(onConnected).toHaveBeenCalledOnce()
    expect(result.current.connectionState).toBe('open')
    expect(result.current.openCount).toBe(1)
  })

  it('tracks reconnect transitions after error then open', () => {
    const { result } = renderHook(() => useSSE('retro-123', {}))

    expect(result.current.connectionState).toBe('connecting')
    expect(result.current.openCount).toBe(0)

    act(() => {
      latestInstance().triggerOpen()
    })

    expect(result.current.connectionState).toBe('open')
    expect(result.current.openCount).toBe(1)

    act(() => {
      latestInstance().triggerError()
    })

    expect(result.current.connectionState).toBe('connecting')
    expect(result.current.openCount).toBe(1)

    act(() => {
      latestInstance().triggerOpen()
    })

    expect(result.current.connectionState).toBe('open')
    expect(result.current.openCount).toBe(2)
  })

  it('keeps signaledVersion monotonic when older envelopes arrive later', () => {
    const { result } = renderHook(() => useSSE('retro-123', {}))

    act(() => {
      latestInstance().triggerEvent(
        EventType.STEP_ADVANCED,
        '{"syncVersion":9,"payload":null}'
      )
    })

    expect(result.current.signaledVersion).toBe(9)

    act(() => {
      latestInstance().triggerEvent(
        EventType.STEP_ADVANCED,
        '{"syncVersion":4,"payload":null}'
      )
    })

    expect(result.current.signaledVersion).toBe(9)
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
