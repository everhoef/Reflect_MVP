import { render, screen, act, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import RetroPage from './RetroPage'
import { EventType } from '@/types/events'
import { useSSESubscription } from '@/hooks/useSSEContext'

type SSEHandlers = Partial<Record<EventType, (rawData: string) => void>>
type OnConnected = (() => void) | undefined

interface CapturedSSE {
  handlers: SSEHandlers
  onConnected: OnConnected
}

const capturedSSE: CapturedSSE = { handlers: {}, onConnected: undefined }

vi.mock('@/hooks/useSSE', () => ({
  useSSE: (
    _retroId: string | undefined,
    handlers: SSEHandlers,
    onConnected?: () => void
  ) => {
    capturedSSE.handlers = handlers
    capturedSSE.onConnected = onConnected
  },
}))

vi.mock('@/components/ComponentRouter', () => ({
  ComponentRouter: () => <TestComponentSubscriber />,
}))

const subscriberEvents: Array<{ type: EventType; data: string }> = []

function TestComponentSubscriber() {
  useSSESubscription(EventType.NOTE_ADDED, (data) => {
    subscriberEvents.push({ type: EventType.NOTE_ADDED, data })
  })
  useSSESubscription(EventType.NOTE_UPDATED, (data) => {
    subscriberEvents.push({ type: EventType.NOTE_UPDATED, data })
  })
  useSSESubscription(EventType.NOTE_DELETED, (data) => {
    subscriberEvents.push({ type: EventType.NOTE_DELETED, data })
  })
  useSSESubscription(EventType.VOTE_ADDED, (data) => {
    subscriberEvents.push({ type: EventType.VOTE_ADDED, data })
  })
  useSSESubscription(EventType.VOTE_REMOVED, (data) => {
    subscriberEvents.push({ type: EventType.VOTE_REMOVED, data })
  })
  return <div data-testid="test-component-subscriber" />
}

const RETRO_ID = 'test-retro-123'

const baseState = {
  retroId: RETRO_ID,
  phase: 'SET_THE_STAGE',
  currentStepId: 1,
  currentStepIndex: 0,
  steps: [
    {
      id: 1,
      title: 'Happiness Check',
      componentType: 'MULTI_COLUMN_BOARD',
      advancementTrigger: 'FACILITATOR_CLICK',
      durationSeconds: null,
      componentConfig: {},
      guidance: null,
    },
  ],
  facilitatorId: 'facilitator-abc',
  isFacilitator: false,
  participantCount: 1,
}

const baseParticipants = [
  { participantId: 'p1', displayName: 'Alice', role: 'PARTICIPANT' },
]

function buildQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  })
}

function renderRetroPage(queryClient: QueryClient) {
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/retro/${RETRO_ID}`]}>
        <Routes>
          <Route path="/retro/:retroId" element={<RetroPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  )
}

function mockFetchSuccess(stateOverride?: Partial<typeof baseState>, participantsOverride?: typeof baseParticipants) {
  const state = { ...baseState, ...stateOverride }
  const participants = participantsOverride ?? baseParticipants
  vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
    const url = input instanceof Request ? input.url : String(input)
    if (url.includes('/state')) {
      return Promise.resolve(new Response(JSON.stringify(state), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    }
    if (url.includes('/participants')) {
      return Promise.resolve(new Response(JSON.stringify(participants), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    }
    return Promise.resolve(new Response('{}', { status: 404 }))
  })
}

beforeEach(() => {
  capturedSSE.handlers = {}
  capturedSSE.onConnected = undefined
  subscriberEvents.length = 0
  vi.restoreAllMocks()
})

describe('RetroPage SSE routing', () => {
  it('renders data-sse-connected=false before onConnected fires', async () => {
    mockFetchSuccess()
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('retro-content')).toBeInTheDocument()
    })

    expect(screen.getByTestId('retro-content')).toHaveAttribute('data-sse-connected', 'false')
  })

  it('sets data-sse-connected=true after onConnected fires', async () => {
    mockFetchSuccess()
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('retro-content')).toBeInTheDocument()
    })

    act(() => {
      capturedSSE.onConnected?.()
    })

    expect(screen.getByTestId('retro-content')).toHaveAttribute('data-sse-connected', 'true')
  })

  it('refetches retro state when STEP_ADVANCED fires', async () => {
    const fetchSpy = vi.fn()
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = input instanceof Request ? input.url : String(input)
      if (url.includes('/state')) {
        fetchSpy(url)
        return Promise.resolve(new Response(JSON.stringify(baseState), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      if (url.includes('/participants')) {
        return Promise.resolve(new Response(JSON.stringify(baseParticipants), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      return Promise.resolve(new Response('{}', { status: 404 }))
    })

    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('retro-content')).toBeInTheDocument()
    })

    const callCountBeforeEvent = fetchSpy.mock.calls.length

    act(() => {
      capturedSSE.handlers[EventType.STEP_ADVANCED]?.('"refresh"')
    })

    await waitFor(() => {
      expect(fetchSpy.mock.calls.length).toBeGreaterThan(callCountBeforeEvent)
    })
  })

  it('refetches retro state when SESSION_STARTED fires', async () => {
    const fetchSpy = vi.fn()
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = input instanceof Request ? input.url : String(input)
      if (url.includes('/state')) {
        fetchSpy(url)
        return Promise.resolve(new Response(JSON.stringify(baseState), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      if (url.includes('/participants')) {
        return Promise.resolve(new Response(JSON.stringify(baseParticipants), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      return Promise.resolve(new Response('{}', { status: 404 }))
    })

    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('retro-content')).toBeInTheDocument()
    })

    const callCountBeforeEvent = fetchSpy.mock.calls.length

    act(() => {
      capturedSSE.handlers[EventType.SESSION_STARTED]?.('"refresh"')
    })

    await waitFor(() => {
      expect(fetchSpy.mock.calls.length).toBeGreaterThan(callCountBeforeEvent)
    })
  })

  it('refetches retro state when PHASE_STARTED fires', async () => {
    const fetchSpy = vi.fn()
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = input instanceof Request ? input.url : String(input)
      if (url.includes('/state')) {
        fetchSpy(url)
        return Promise.resolve(new Response(JSON.stringify(baseState), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      if (url.includes('/participants')) {
        return Promise.resolve(new Response(JSON.stringify(baseParticipants), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      return Promise.resolve(new Response('{}', { status: 404 }))
    })

    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('retro-content')).toBeInTheDocument()
    })

    const callCountBeforeEvent = fetchSpy.mock.calls.length

    act(() => {
      capturedSSE.handlers[EventType.PHASE_STARTED]?.('SET_THE_STAGE')
    })

    await waitFor(() => {
      expect(fetchSpy.mock.calls.length).toBeGreaterThan(callCountBeforeEvent)
    })
  })

  it('refetches participants when PARTICIPANT_JOINED fires', async () => {
    const participantFetchSpy = vi.fn()
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = input instanceof Request ? input.url : String(input)
      if (url.includes('/state')) {
        return Promise.resolve(new Response(JSON.stringify(baseState), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      if (url.includes('/participants')) {
        participantFetchSpy(url)
        return Promise.resolve(new Response(JSON.stringify(baseParticipants), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      return Promise.resolve(new Response('{}', { status: 404 }))
    })

    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('retro-content')).toBeInTheDocument()
    })

    const callCountBeforeEvent = participantFetchSpy.mock.calls.length

    act(() => {
      capturedSSE.handlers[EventType.PARTICIPANT_JOINED]?.('"Bob"')
    })

    await waitFor(() => {
      expect(participantFetchSpy.mock.calls.length).toBeGreaterThan(callCountBeforeEvent)
    })
  })

  it('refetches participants when PARTICIPANT_LEFT fires', async () => {
    const participantFetchSpy = vi.fn()
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = input instanceof Request ? input.url : String(input)
      if (url.includes('/state')) {
        return Promise.resolve(new Response(JSON.stringify(baseState), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      if (url.includes('/participants')) {
        participantFetchSpy(url)
        return Promise.resolve(new Response(JSON.stringify(baseParticipants), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      return Promise.resolve(new Response('{}', { status: 404 }))
    })

    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('retro-content')).toBeInTheDocument()
    })

    const callCountBeforeEvent = participantFetchSpy.mock.calls.length

    act(() => {
      capturedSSE.handlers[EventType.PARTICIPANT_LEFT]?.('"Alice"')
    })

    await waitFor(() => {
      expect(participantFetchSpy.mock.calls.length).toBeGreaterThan(callCountBeforeEvent)
    })
  })

  it('dispatches NOTE_ADDED through SSEContext so subscribed child receives it', async () => {
    mockFetchSuccess()
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('test-component-subscriber')).toBeInTheDocument()
    })

    act(() => {
      capturedSSE.handlers[EventType.NOTE_ADDED]?.('{"responseId":"n1"}')
    })

    expect(subscriberEvents).toContainEqual({ type: EventType.NOTE_ADDED, data: '{"responseId":"n1"}' })
  })

  it('dispatches NOTE_UPDATED through SSEContext so subscribed child receives it', async () => {
    mockFetchSuccess()
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('test-component-subscriber')).toBeInTheDocument()
    })

    act(() => {
      capturedSSE.handlers[EventType.NOTE_UPDATED]?.('{"responseId":"n2"}')
    })

    expect(subscriberEvents).toContainEqual({ type: EventType.NOTE_UPDATED, data: '{"responseId":"n2"}' })
  })

  it('dispatches NOTE_DELETED through SSEContext so subscribed child receives it', async () => {
    mockFetchSuccess()
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('test-component-subscriber')).toBeInTheDocument()
    })

    act(() => {
      capturedSSE.handlers[EventType.NOTE_DELETED]?.('{"responseId":"n3"}')
    })

    expect(subscriberEvents).toContainEqual({ type: EventType.NOTE_DELETED, data: '{"responseId":"n3"}' })
  })

  it('dispatches VOTE_ADDED through SSEContext so subscribed child receives it', async () => {
    mockFetchSuccess()
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('test-component-subscriber')).toBeInTheDocument()
    })

    act(() => {
      capturedSSE.handlers[EventType.VOTE_ADDED]?.('{"responseId":"v1"}')
    })

    expect(subscriberEvents).toContainEqual({ type: EventType.VOTE_ADDED, data: '{"responseId":"v1"}' })
  })

  it('dispatches VOTE_REMOVED through SSEContext so subscribed child receives it', async () => {
    mockFetchSuccess()
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('test-component-subscriber')).toBeInTheDocument()
    })

    act(() => {
      capturedSSE.handlers[EventType.VOTE_REMOVED]?.('{"responseId":"v2"}')
    })

    expect(subscriberEvents).toContainEqual({ type: EventType.VOTE_REMOVED, data: '{"responseId":"v2"}' })
  })
})
