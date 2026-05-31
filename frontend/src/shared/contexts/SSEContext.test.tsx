import { render, act } from '@testing-library/react'
import { useEffect, useRef, useContext } from 'react'
import { SSEProvider, SSEContext } from '@/shared/contexts/SSEContext'
import { useSSESubscription } from '@/shared/hooks/useSSEContext'
import { EventType } from '@/shared/types/events'

type Dispatch = (eventType: EventType, rawData: string) => void

function useTestDispatch(dispatchHolder: { current: Dispatch | null }) {
  const ctx = useContext(SSEContext)
  const holderRef = useRef(dispatchHolder)
  useEffect(() => {
    holderRef.current.current = ctx?.dispatch ?? null
  })
}

function DispatchCapture({ holder }: { holder: { current: Dispatch | null } }) {
  useTestDispatch(holder)
  return null
}

describe('SSEContext / SSEProvider', () => {
  it('delivers a dispatched event to a single subscriber', () => {
    const received: string[] = []
    const dispatch = { current: null as Dispatch | null }

    function Subscriber() {
      useSSESubscription(EventType.NOTE_ADDED, (data) => {
        received.push(data)
      })
      return null
    }

    render(
      <SSEProvider>
        <DispatchCapture holder={dispatch} />
        <Subscriber />
      </SSEProvider>
    )

    act(() => {
      dispatch.current!(EventType.NOTE_ADDED, '{"responseId":"abc"}')
    })

    expect(received).toHaveLength(1)
    expect(received[0]).toBe('{"responseId":"abc"}')
  })

  it('delivers a dispatched event to multiple subscribers of the same type', () => {
    const receivedA: string[] = []
    const receivedB: string[] = []
    const dispatch = { current: null as Dispatch | null }

    function SubscriberA() {
      useSSESubscription(EventType.NOTE_ADDED, (data) => {
        receivedA.push(data)
      })
      return null
    }

    function SubscriberB() {
      useSSESubscription(EventType.NOTE_ADDED, (data) => {
        receivedB.push(data)
      })
      return null
    }

    render(
      <SSEProvider>
        <DispatchCapture holder={dispatch} />
        <SubscriberA />
        <SubscriberB />
      </SSEProvider>
    )

    act(() => {
      dispatch.current!(EventType.NOTE_ADDED, '"payload"')
    })

    expect(receivedA).toHaveLength(1)
    expect(receivedB).toHaveLength(1)
  })

  it('does NOT deliver events to subscribers of a different event type', () => {
    const noteAddedReceived: string[] = []
    const voteAddedReceived: string[] = []
    const dispatch = { current: null as Dispatch | null }

    function NoteSubscriber() {
      useSSESubscription(EventType.NOTE_ADDED, (data) => {
        noteAddedReceived.push(data)
      })
      return null
    }

    function VoteSubscriber() {
      useSSESubscription(EventType.VOTE_ADDED, (data) => {
        voteAddedReceived.push(data)
      })
      return null
    }

    render(
      <SSEProvider>
        <DispatchCapture holder={dispatch} />
        <NoteSubscriber />
        <VoteSubscriber />
      </SSEProvider>
    )

    act(() => {
      dispatch.current!(EventType.NOTE_ADDED, '"note-data"')
    })

    expect(noteAddedReceived).toHaveLength(1)
    expect(voteAddedReceived).toHaveLength(0)
  })

  it('stops delivering events to a subscriber after it unmounts', () => {
    const received: string[] = []
    const dispatch = { current: null as Dispatch | null }

    function Subscriber() {
      useSSESubscription(EventType.NOTE_DELETED, (data) => {
        received.push(data)
      })
      return null
    }

    const { unmount } = render(
      <SSEProvider>
        <DispatchCapture holder={dispatch} />
        <Subscriber />
      </SSEProvider>
    )

    act(() => {
      dispatch.current!(EventType.NOTE_DELETED, '"first"')
    })
    expect(received).toHaveLength(1)

    unmount()

    act(() => {
      dispatch.current!(EventType.NOTE_DELETED, '"second"')
    })
    expect(received).toHaveLength(1)
  })

  it('uses the latest handler after a rerender (no stale closure)', () => {
    const firstHandler = vi.fn()
    const secondHandler = vi.fn()
    const dispatch = { current: null as Dispatch | null }

    function LatestHandlerSubscriber({ handler }: { handler: (d: string) => void }) {
      useSSESubscription(EventType.VOTE_REMOVED, handler)
      return null
    }

    const { rerender } = render(
      <SSEProvider>
        <DispatchCapture holder={dispatch} />
        <LatestHandlerSubscriber handler={firstHandler} />
      </SSEProvider>
    )

    rerender(
      <SSEProvider>
        <DispatchCapture holder={dispatch} />
        <LatestHandlerSubscriber handler={secondHandler} />
      </SSEProvider>
    )

    act(() => {
      dispatch.current!(EventType.VOTE_REMOVED, '"vote-data"')
    })

    expect(firstHandler).not.toHaveBeenCalled()
    expect(secondHandler).toHaveBeenCalledOnce()
    expect(secondHandler).toHaveBeenCalledWith('"vote-data"')
  })

  it('gracefully continues dispatching to other handlers when one throws', () => {
    const goodHandler = vi.fn()
    const dispatch = { current: null as Dispatch | null }

    function ThrowingSubscriber() {
      useSSESubscription(EventType.NOTE_UPDATED, () => {
        throw new Error('handler error')
      })
      return null
    }

    function GoodSubscriber() {
      useSSESubscription(EventType.NOTE_UPDATED, goodHandler)
      return null
    }

    const consoleSpy = vi.spyOn(console, 'warn').mockImplementation(() => undefined)

    render(
      <SSEProvider>
        <DispatchCapture holder={dispatch} />
        <ThrowingSubscriber />
        <GoodSubscriber />
      </SSEProvider>
    )

    act(() => {
      dispatch.current!(EventType.NOTE_UPDATED, '"data"')
    })

    expect(goodHandler).toHaveBeenCalledOnce()
    consoleSpy.mockRestore()
  })
})

describe('SSEProvider subscribe/unsubscribe', () => {
  it('returns an unsubscribe function that removes the handler', () => {
    const received: string[] = []
    const dispatch = { current: null as Dispatch | null }
    const unsubscribeRef = { current: (() => void 0) as () => void }

    function DirectSubscriber() {
      const ctx = useContext(SSEContext)
      const innerRef = useRef<(() => void) | null>(null)
      useEffect(() => {
        if (!ctx) return
        innerRef.current = ctx.subscribe(EventType.VOTE_ADDED, (data) => {
          received.push(data)
        })
        unsubscribeRef.current = innerRef.current
        return () => {
          innerRef.current?.()
        }
      }, [ctx])
      return null
    }

    render(
      <SSEProvider>
        <DispatchCapture holder={dispatch} />
        <DirectSubscriber />
      </SSEProvider>
    )

    act(() => {
      dispatch.current!(EventType.VOTE_ADDED, '"v1"')
    })
    expect(received).toHaveLength(1)

    act(() => {
      unsubscribeRef.current()
    })

    act(() => {
      dispatch.current!(EventType.VOTE_ADDED, '"v2"')
    })
    expect(received).toHaveLength(1)
  })
})


