import { render, screen, act, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, Outlet } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import RetroPage from '@/modules/facilitation/pages/RetroPage'
import { retroPhaseToStageIndex, STAGE_LABELS } from '@/modules/facilitation/lib/retroPhaseToStageIndex'
import { EventType } from '@/shared/types/events'
import { useAppliedVersionStore } from '@/modules/facilitation/hooks/api/useRetro'
import type { UseSSETransportState } from '@/shared/hooks/useSSE'
import { useSSESubscription } from '@/shared/hooks/useSSEContext'
import { useRetroStateStore } from '@/modules/facilitation/store/retroStore'
import { useAssistantStore } from '@/modules/facilitation/store/assistantStore'

type SSEHandlers = Partial<Record<EventType, (rawData: string) => void>>
type OnConnected = (() => void) | undefined

interface CapturedSSE {
  handlers: SSEHandlers
  onConnected: OnConnected
  transportState: UseSSETransportState
}

const capturedSSE: CapturedSSE = {
  handlers: {},
  onConnected: undefined,
  transportState: { signaledVersion: null, connectionState: 'connecting', openCount: 0 },
}

function setCapturedTransportState(next: Partial<UseSSETransportState>) {
  capturedSSE.transportState = { ...capturedSSE.transportState, ...next }
}

vi.mock('@/shared/hooks/useSSE', () => ({
  useSSE: (
    _retroId: string | undefined,
    handlers: SSEHandlers,
    onConnected?: () => void
  ) => {
    capturedSSE.handlers = handlers
    capturedSSE.onConnected = onConnected
    return capturedSSE.transportState
  },
}))

vi.mock('@/modules/facilitation/components/ComponentRouter', () => ({
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
  syncVersion: 1,
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

function TestLayout() {
  const currentPhase = useRetroStateStore((s) => s.currentPhase)
  const currentStage = retroPhaseToStageIndex(currentPhase)

  return (
    <div>
      <nav data-testid="stage-progress-bar">
        {STAGE_LABELS.map((label, index) => {
          const stageId = index + 1
          const isCurrent = stageId === currentStage
          return (
            <div key={stageId} aria-current={isCurrent ? 'step' : undefined}>
              {label}
            </div>
          )
        })}
      </nav>
      <Outlet />
    </div>
  )
}

function retroPageTree(queryClient: QueryClient) {
  return (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/retro/${RETRO_ID}`]}>
        <Routes>
          <Route element={<TestLayout />}>
            <Route path="/retro/:retroId" element={<RetroPage />} />
          </Route>
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  )
}

function renderRetroPage(queryClient: QueryClient) {
  return render(retroPageTree(queryClient))
}

function renderRetroPageWithLayout(queryClient: QueryClient) {
  return render(retroPageTree(queryClient))
}

function mockFetchSuccess(stateOverride?: Partial<typeof baseState>, participantsOverride?: typeof baseParticipants) {
  const state = { ...baseState, ...stateOverride }
  const participants = participantsOverride ?? baseParticipants
  vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
    const url = input instanceof Request ? input.url : String(input)
    if (url.match(/\/retros\/[^/]+$/)) {
      return Promise.resolve(new Response(JSON.stringify(state), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    }
    if (url.includes('/participants')) {
      return Promise.resolve(new Response(JSON.stringify(participants), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    }
    if (url.includes('/timer')) {
      return Promise.resolve(new Response(null, { status: 204 }))
    }
    if (url.includes('/actions')) {
      return Promise.resolve(new Response(JSON.stringify([]), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    }
    if (url.includes('/escalations')) {
      return Promise.resolve(new Response(JSON.stringify([]), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    }
    return Promise.resolve(new Response('{}', { status: 404 }))
  })
}

beforeEach(() => {
  capturedSSE.handlers = {}
  capturedSSE.onConnected = undefined
  capturedSSE.transportState = { signaledVersion: null, connectionState: 'connecting', openCount: 0 }
  subscriberEvents.length = 0
  vi.restoreAllMocks()
  useAssistantStore.getState().clearAssistant()
  useAppliedVersionStore.setState({ appliedVersionByRetroId: {} })
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

  it('sets data-sse-connected truthfully from transport connection state', async () => {
    mockFetchSuccess()
    const queryClient = buildQueryClient()
    const view = renderRetroPage(queryClient)

    await waitFor(() => {
      expect(screen.getByTestId('retro-content')).toBeInTheDocument()
    })

    setCapturedTransportState({ connectionState: 'open', openCount: 1 })
    view.rerender(retroPageTree(queryClient))

    expect(screen.getByTestId('retro-content')).toHaveAttribute('data-sse-connected', 'true')

    setCapturedTransportState({ connectionState: 'connecting' })
    view.rerender(retroPageTree(queryClient))

    expect(screen.getByTestId('retro-content')).toHaveAttribute('data-sse-connected', 'false')
  })

  it('renders data-sync-state="unknown" when versions are not available', async () => {
    mockFetchSuccess()
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('retro-content')).toBeInTheDocument()
    })

    expect(screen.getByTestId('retro-content')).toHaveAttribute('data-sync-state', 'unknown')
  })

  it('renders data-sync-state="reconciling" when signaledVersion is ahead of appliedVersion', async () => {
    mockFetchSuccess()
    const queryClient = buildQueryClient()
    const view = renderRetroPage(queryClient)

    await waitFor(() => {
      expect(screen.getByTestId('retro-content')).toBeInTheDocument()
    })

    useAppliedVersionStore.setState({ appliedVersionByRetroId: { [RETRO_ID]: 5 } })
    setCapturedTransportState({ signaledVersion: 6 })
    view.rerender(retroPageTree(queryClient))

    expect(screen.getByTestId('retro-content')).toHaveAttribute('data-sync-state', 'reconciling')
  })

  it('renders data-sync-state="settled" when signaledVersion matches appliedVersion', async () => {
    mockFetchSuccess()
    const queryClient = buildQueryClient()
    const view = renderRetroPage(queryClient)

    await waitFor(() => {
      expect(screen.getByTestId('retro-content')).toBeInTheDocument()
    })

    useAppliedVersionStore.setState({ appliedVersionByRetroId: { [RETRO_ID]: 5 } })
    setCapturedTransportState({ signaledVersion: 5 })
    view.rerender(retroPageTree(queryClient))

    expect(screen.getByTestId('retro-content')).toHaveAttribute('data-sync-state', 'settled')
  })

  it('refetches the bounded bundle on SSE open/reconnect transitions', async () => {
    const fetchCounts = { state: 0, participants: 0, timer: 0, actions: 0, escalations: 0 }

    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = input instanceof Request ? input.url : String(input)
      if (url.match(/\/retros\/[^/]+$/)) {
        fetchCounts.state += 1
        return Promise.resolve(new Response(JSON.stringify(baseState), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      if (url.includes('/participants')) {
        fetchCounts.participants += 1
        return Promise.resolve(new Response(JSON.stringify(baseParticipants), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      if (url.includes('/timer')) {
        fetchCounts.timer += 1
        return Promise.resolve(new Response(null, { status: 204 }))
      }
      if (url.includes('/actions')) {
        fetchCounts.actions += 1
        return Promise.resolve(new Response(JSON.stringify([]), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      if (url.includes('/escalations')) {
        fetchCounts.escalations += 1
        return Promise.resolve(new Response(JSON.stringify([]), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      return Promise.resolve(new Response('{}', { status: 404 }))
    })

    const queryClient = buildQueryClient()
    const view = renderRetroPage(queryClient)

    await waitFor(() => {
      expect(screen.getByTestId('retro-content')).toBeInTheDocument()
    })

    const baseline = { ...fetchCounts }
    setCapturedTransportState({ connectionState: 'open', openCount: 1 })
    view.rerender(retroPageTree(queryClient))

    await waitFor(() => {
      expect(fetchCounts.state).toBeGreaterThan(baseline.state)
      expect(fetchCounts.participants).toBeGreaterThan(baseline.participants)
      expect(fetchCounts.timer).toBeGreaterThan(baseline.timer)
      expect(fetchCounts.actions).toBeGreaterThan(baseline.actions)
      expect(fetchCounts.escalations).toBeGreaterThan(baseline.escalations)
    })
  })

  it('refetches the bounded bundle when signaledVersion is ahead of appliedVersion', async () => {
    let stateSyncVersion = 1
    const fetchCounts = { state: 0, participants: 0, timer: 0, actions: 0, escalations: 0 }

    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = input instanceof Request ? input.url : String(input)
      if (url.match(/\/retros\/[^/]+$/)) {
        fetchCounts.state += 1
        return Promise.resolve(new Response(JSON.stringify({ ...baseState, syncVersion: stateSyncVersion }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      if (url.includes('/participants')) {
        fetchCounts.participants += 1
        return Promise.resolve(new Response(JSON.stringify(baseParticipants), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      if (url.includes('/timer')) {
        fetchCounts.timer += 1
        return Promise.resolve(new Response(null, { status: 204 }))
      }
      if (url.includes('/actions')) {
        fetchCounts.actions += 1
        return Promise.resolve(new Response(JSON.stringify([]), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      if (url.includes('/escalations')) {
        fetchCounts.escalations += 1
        return Promise.resolve(new Response(JSON.stringify([]), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      return Promise.resolve(new Response('{}', { status: 404 }))
    })

    const queryClient = buildQueryClient()
    const view = renderRetroPage(queryClient)

    await waitFor(() => {
      expect(screen.getByTestId('retro-content')).toBeInTheDocument()
    })

    const baseline = { ...fetchCounts }
    stateSyncVersion = 5
    setCapturedTransportState({ signaledVersion: 5 })
    view.rerender(retroPageTree(queryClient))

    await waitFor(() => {
      expect(fetchCounts.state).toBeGreaterThan(baseline.state)
      expect(fetchCounts.participants).toBeGreaterThan(baseline.participants)
      expect(fetchCounts.timer).toBeGreaterThan(baseline.timer)
      expect(fetchCounts.actions).toBeGreaterThan(baseline.actions)
      expect(fetchCounts.escalations).toBeGreaterThan(baseline.escalations)
    })
  })

  it('reconnect/open recovery converges stale participants from the authoritative bundle', async () => {
    let participantsState = [{ participantId: 'p1', displayName: 'Alice', role: 'PARTICIPANT' }]

    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = input instanceof Request ? input.url : String(input)
      if (url.match(/\/retros\/[^/]+$/)) {
        return Promise.resolve(new Response(JSON.stringify(baseState), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      if (url.includes('/participants')) {
        return Promise.resolve(new Response(JSON.stringify(participantsState), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      if (url.includes('/timer')) {
        return Promise.resolve(new Response(null, { status: 204 }))
      }
      if (url.includes('/actions')) {
        return Promise.resolve(new Response(JSON.stringify([]), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      if (url.includes('/escalations')) {
        return Promise.resolve(new Response(JSON.stringify([]), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      return Promise.resolve(new Response('{}', { status: 404 }))
    })

    const queryClient = buildQueryClient()
    const view = renderRetroPage(queryClient)

    await waitFor(() => {
      expect(screen.getByText('Participants (1)')).toBeInTheDocument()
      expect(screen.getByText('Alice')).toBeInTheDocument()
    })

    participantsState = [
      { participantId: 'p1', displayName: 'Alice', role: 'PARTICIPANT' },
      { participantId: 'p2', displayName: 'Bob', role: 'PARTICIPANT' },
    ]

    setCapturedTransportState({ connectionState: 'open', openCount: 1 })
    view.rerender(retroPageTree(queryClient))

    await waitFor(() => {
      expect(screen.getByText('Participants (2)')).toBeInTheDocument()
      expect(screen.getByText('Bob')).toBeInTheDocument()
    })
  })

  it('version mismatch recovery converges stale participants when signaledVersion moves ahead', async () => {
    let stateSyncVersion = 1
    let participantsState = [{ participantId: 'p1', displayName: 'Alice', role: 'PARTICIPANT' }]

    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = input instanceof Request ? input.url : String(input)
      if (url.match(/\/retros\/[^/]+$/)) {
        return Promise.resolve(new Response(JSON.stringify({ ...baseState, syncVersion: stateSyncVersion }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      if (url.includes('/participants')) {
        return Promise.resolve(new Response(JSON.stringify(participantsState), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      if (url.includes('/timer')) {
        return Promise.resolve(new Response(null, { status: 204 }))
      }
      if (url.includes('/actions')) {
        return Promise.resolve(new Response(JSON.stringify([]), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      if (url.includes('/escalations')) {
        return Promise.resolve(new Response(JSON.stringify([]), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      return Promise.resolve(new Response('{}', { status: 404 }))
    })

    const queryClient = buildQueryClient()
    const view = renderRetroPage(queryClient)

    await waitFor(() => {
      expect(screen.getByText('Participants (1)')).toBeInTheDocument()
      expect(screen.getByText('Alice')).toBeInTheDocument()
    })

    stateSyncVersion = 5
    participantsState = [
      { participantId: 'p1', displayName: 'Alice', role: 'PARTICIPANT' },
      { participantId: 'p2', displayName: 'Bob', role: 'PARTICIPANT' },
    ]

    setCapturedTransportState({ signaledVersion: 5 })
    view.rerender(retroPageTree(queryClient))

    await waitFor(() => {
      expect(screen.getByText('Participants (2)')).toBeInTheDocument()
      expect(screen.getByText('Bob')).toBeInTheDocument()
    })
  })

  it('refetches retro state when STEP_ADVANCED fires', async () => {
    const fetchSpy = vi.fn()
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = input instanceof Request ? input.url : String(input)
      if (url.match(/\/retros\/[^/]+$/)) {
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
      capturedSSE.handlers[EventType.STEP_ADVANCED]?.('null')
    })

    await waitFor(() => {
      expect(fetchSpy.mock.calls.length).toBeGreaterThan(callCountBeforeEvent)
    })
  })

  it('refetches retro state when SESSION_STARTED fires', async () => {
    const fetchSpy = vi.fn()
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = input instanceof Request ? input.url : String(input)
      if (url.match(/\/retros\/[^/]+$/)) {
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
      capturedSSE.handlers[EventType.SESSION_STARTED]?.('null')
    })

    await waitFor(() => {
      expect(fetchSpy.mock.calls.length).toBeGreaterThan(callCountBeforeEvent)
    })
  })

  it('refetches retro state when PHASE_STARTED fires', async () => {
    const fetchSpy = vi.fn()
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = input instanceof Request ? input.url : String(input)
      if (url.match(/\/retros\/[^/]+$/)) {
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
      if (url.match(/\/retros\/[^/]+$/)) {
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
      if (url.match(/\/retros\/[^/]+$/)) {
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

describe('RetroPage phase-progress bar (canonical behavior)', () => {
  const gatherDataState = {
    ...baseState,
    phase: 'GATHER_DATA',
    currentStepId: 2,
    currentStepIndex: 1,
    steps: [
      {
        id: 1,
        title: 'Check-in',
        componentType: 'MULTI_COLUMN_BOARD',
        advancementTrigger: 'FACILITATOR_CLICK',
        durationSeconds: null,
        componentConfig: {},
        guidance: null,
      },
      {
        id: 2,
        title: 'Mad Sad Glad',
        componentType: 'MULTI_COLUMN_BOARD',
        advancementTrigger: 'FACILITATOR_CLICK',
        durationSeconds: null,
        componentConfig: {},
        guidance: null,
      },
    ],
  }

  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('Test A: renders exactly one stage-progress bar on the retro page', async () => {
    mockFetchSuccess(gatherDataState)
    renderRetroPageWithLayout(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('retro-content')).toBeInTheDocument()
    })

    const bars = screen.getAllByTestId('stage-progress-bar')
    expect(bars).toHaveLength(1)
  })

  it('Test B: active stage in the progress bar matches the backend phase', async () => {
    mockFetchSuccess(gatherDataState)
    renderRetroPageWithLayout(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('retro-content')).toBeInTheDocument()
    })

    const bars = screen.getAllByTestId('stage-progress-bar')
    const headerBar = bars[0]!
    const currentStep = headerBar.querySelector('[aria-current="step"]')
    expect(currentStep).not.toBeNull()
    expect(currentStep).toHaveTextContent('Gather Data')
  })
})

describe('RetroPage lobby phase', () => {
  const lobbyState = {
    ...baseState,
    phase: 'LOBBY',
    currentStepId: null,
    currentStepIndex: 0,
    steps: [],
  }

  beforeEach(() => {
    vi.restoreAllMocks()
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText: vi.fn().mockResolvedValue(undefined) },
      configurable: true,
      writable: true,
    })
  })

  function mockLobbyFetch() {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = input instanceof Request ? input.url : String(input)
      if (url.match(/\/retros\/[^/]+$/)) {
        return Promise.resolve(new Response(JSON.stringify(lobbyState), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      if (url.includes('/participants')) {
        return Promise.resolve(new Response(JSON.stringify(baseParticipants), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      return Promise.resolve(new Response('{}', { status: 404 }))
    })
  }

  it('shows the retro ID in the lobby', async () => {
    mockLobbyFetch()
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('retro-id-display')).toBeInTheDocument()
    })

    expect(screen.getByTestId('retro-id-display')).toHaveTextContent(RETRO_ID)
  })

  it('renders the copy button in the lobby', async () => {
    mockLobbyFetch()
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('copy-retro-id-button')).toBeInTheDocument()
    })

    expect(screen.getByTestId('copy-retro-id-button')).toHaveTextContent('Copy')
  })

  it('copies the retro ID when copy button is clicked and shows Copied! feedback', async () => {
    mockLobbyFetch()
    const user = userEvent.setup()
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('copy-retro-id-button')).toBeInTheDocument()
    })

    await user.click(screen.getByTestId('copy-retro-id-button'))

    await waitFor(() => {
      expect(screen.getByTestId('copy-retro-id-button')).toHaveTextContent('Copied!')
    })
  })

  it('does NOT show retro ID display when not in LOBBY phase', async () => {
    mockFetchSuccess()
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('retro-content')).toBeInTheDocument()
    })

    expect(screen.queryByTestId('retro-id-display')).not.toBeInTheDocument()
    expect(screen.queryByTestId('copy-retro-id-button')).not.toBeInTheDocument()
  })
})

describe('RetroPage facilitator role', () => {
  const lobbyFacilitatorState = {
    ...baseState,
    phase: 'LOBBY',
    currentStepId: null,
    currentStepIndex: 0,
    steps: [],
    isFacilitator: true,
  }

  const lobbyParticipantState = {
    ...baseState,
    phase: 'LOBBY',
    currentStepId: null,
    currentStepIndex: 0,
    steps: [],
    isFacilitator: false,
  }

  function mockLobbyFetchWithState(state: typeof lobbyFacilitatorState) {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = input instanceof Request ? input.url : String(input)
      if (url.match(/\/retros\/[^/]+$/)) {
        return Promise.resolve(new Response(JSON.stringify(state), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      if (url.includes('/participants')) {
        return Promise.resolve(new Response(JSON.stringify(baseParticipants), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      return Promise.resolve(new Response('{}', { status: 404 }))
    })
  }

  it('shows Start Retrospective button when isFacilitator is true', async () => {
    mockLobbyFetchWithState(lobbyFacilitatorState)
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('start-retro-button')).toBeInTheDocument()
    })

    expect(screen.getByTestId('start-retro-button')).toHaveTextContent('Start Retrospective')
  })

  it('hides Start Retrospective button when isFacilitator is false', async () => {
    mockLobbyFetchWithState(lobbyParticipantState)
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('retro-id-display')).toBeInTheDocument()
    })

    expect(screen.queryByTestId('start-retro-button')).not.toBeInTheDocument()
  })
})

describe('RetroPage timer role visibility', () => {
  const timedStep = {
    id: 10,
    title: 'Timed Activity',
    componentType: 'MULTI_COLUMN_BOARD',
    advancementTrigger: 'TIMER_EXPIRES',
    durationSeconds: 120,
    componentConfig: {},
    guidance: null,
  }

  const timedStepState = {
    ...baseState,
    phase: 'SET_THE_STAGE',
    currentStepId: 10,
    currentStepIndex: 0,
    steps: [timedStep],
  }

  function mockTimedFetch(stateOverride: Partial<typeof timedStepState> = {}, timerOverride?: { remainingSeconds: number; isPaused: boolean; state: string }) {
    const state = { ...timedStepState, ...stateOverride }
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = input instanceof Request ? input.url : String(input)
      if (url.match(/\/retros\/[^/]+$/)) {
        return Promise.resolve(new Response(JSON.stringify(state), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      if (url.includes('/participants')) {
        return Promise.resolve(new Response(JSON.stringify(baseParticipants), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      if (url.includes('/timer')) {
        if (timerOverride) {
          return Promise.resolve(new Response(JSON.stringify(timerOverride), { status: 200, headers: { 'Content-Type': 'application/json' } }))
        }
        return Promise.resolve(new Response(null, { status: 204 }))
      }
      return Promise.resolve(new Response('{}', { status: 404 }))
    })
  }

  beforeEach(() => {
    vi.restoreAllMocks()
    useRetroStateStore.setState({ remainingSeconds: null, isPaused: false, timerState: null })
  })

  it('facilitator sees pause button when timer is active and not paused', async () => {
    mockTimedFetch({ isFacilitator: true }, { remainingSeconds: 90, isPaused: false, state: 'yellow' })
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('pause-timer-button')).toBeInTheDocument()
    })

    expect(screen.queryByTestId('resume-timer-button')).not.toBeInTheDocument()
  })

  it('participant does NOT see pause button even when timer is active', async () => {
    mockTimedFetch({ isFacilitator: false }, { remainingSeconds: 90, isPaused: false, state: 'yellow' })
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('retro-content')).toBeInTheDocument()
    })

    await waitFor(() => {
      expect(screen.queryByTestId('pause-timer-button')).not.toBeInTheDocument()
    })
    expect(screen.queryByTestId('resume-timer-button')).not.toBeInTheDocument()
  })

  it('facilitator sees resume button when timer is paused', async () => {
    mockTimedFetch({ isFacilitator: true }, { remainingSeconds: 60, isPaused: true, state: 'yellow' })
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('resume-timer-button')).toBeInTheDocument()
    })

    expect(screen.queryByTestId('pause-timer-button')).not.toBeInTheDocument()
  })
})

describe('RetroPage assistant-store bootstrap wiring', () => {
  const stateWithAssistant = {
    ...baseState,
    assistantState: {
      current: {
        retroId: RETRO_ID,
        stepId: 1,
        stepTitle: 'Happiness Check',
        publicText: 'Ask everyone to rate their mood from 1 to 10.',
      },
      history: [],
      facilitatorCoachingNote: null,
    },
  }

  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('bootstraps assistant store from rawState.assistantState on load', async () => {
    mockFetchSuccess(stateWithAssistant)
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('retro-content')).toBeInTheDocument()
    })

    await waitFor(() => {
      expect(useAssistantStore.getState().current?.publicText).toBe(
        'Ask everyone to rate their mood from 1 to 10.'
      )
    })
  })

  it('GuidanceSidebar shows current message from assistant store, not step.guidance', async () => {
    mockFetchSuccess(stateWithAssistant)
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('guidance-content')).toBeInTheDocument()
    })

    await waitFor(() => {
      expect(screen.getByTestId('guidance-content')).toHaveTextContent(
        'Ask everyone to rate their mood from 1 to 10.'
      )
    })
  })

  it('GuidanceSidebar shows fallback "No guidance" when assistant store current is null', async () => {
    mockFetchSuccess()
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('guidance-content')).toBeInTheDocument()
    })

    expect(screen.getByTestId('guidance-content')).toHaveTextContent(
      'No guidance available for this step.'
    )
  })

  it('facilitator always sees private coaching note (fallback) when store coaching is null', async () => {
    mockFetchSuccess({ ...baseState, isFacilitator: true })
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('retro-content')).toBeInTheDocument()
    })

    await waitFor(() => {
      expect(screen.getByTestId('assistant-private-coaching')).toBeInTheDocument()
    })

    expect(screen.getByTestId('assistant-private-coaching')).toHaveTextContent(
      'Click Next when ready to advance the room to the next step.'
    )
  })

  it('non-facilitator does not see private coaching section', async () => {
    mockFetchSuccess({ ...baseState, isFacilitator: false })
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('retro-content')).toBeInTheDocument()
    })

    await waitFor(() => {
      expect(screen.getByTestId('assistant-private-coaching-placeholder')).toBeInTheDocument()
    })
    expect(screen.queryByTestId('assistant-private-coaching')).not.toBeInTheDocument()
  })

  it('store coaching note overrides fallback when present', async () => {
    const stateWithCoaching = {
      ...baseState,
      isFacilitator: true,
      assistantState: {
        current: null,
        history: [],
        facilitatorCoachingNote: 'Watch for quiet participants.',
      },
    }
    mockFetchSuccess(stateWithCoaching)
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('retro-content')).toBeInTheDocument()
    })

    await waitFor(() => {
      expect(screen.getByTestId('assistant-private-coaching')).toHaveTextContent(
        'Watch for quiet participants.'
      )
    })
  })
})

describe('RetroPage reload/late-join bootstrap path (Task 10)', () => {
  // Server state representing step 3 as current, with 2 previous history items
  const stateAtStep3 = {
    ...baseState,
    assistantState: {
      current: { retroId: RETRO_ID, stepId: 3, stepTitle: 'Step Three', publicText: 'Now on step 3.' },
      history: [
        { retroId: RETRO_ID, stepId: 2, stepTitle: 'Step Two', publicText: 'Was on step 2.' },
        { retroId: RETRO_ID, stepId: 1, stepTitle: 'Step One', publicText: 'Was on step 1.' },
      ],
      facilitatorCoachingNote: null,
    },
  }

  // Server state representing step 4 as current, with 3 previous history items
  const stateAtStep4 = {
    ...baseState,
    currentStepId: 1,
    currentStepIndex: 3,
    assistantState: {
      current: { retroId: RETRO_ID, stepId: 4, stepTitle: 'Step Four', publicText: 'Now on step 4.' },
      history: [
        { retroId: RETRO_ID, stepId: 3, stepTitle: 'Step Three', publicText: 'Was on step 3.' },
        { retroId: RETRO_ID, stepId: 2, stepTitle: 'Step Two', publicText: 'Was on step 2.' },
        { retroId: RETRO_ID, stepId: 1, stepTitle: 'Step One', publicText: 'Was on step 1.' },
      ],
      facilitatorCoachingNote: null,
    },
  }

  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('reload: stale store state is replaced by authoritative server state on remount', async () => {
    // Seed the store with stale data (as if from a previous page visit/state)
    useAssistantStore.setState({
      current: { retroId: RETRO_ID, stepId: 99, stepTitle: 'Stale Step', publicText: 'Stale message from previous render.' },
      history: [
        { retroId: RETRO_ID, stepId: 98, stepTitle: 'Old Step', publicText: 'Old stale history.' },
      ],
      facilitatorCoachingNote: 'Old stale coaching.',
    })

    // Server returns authoritative state at step 3
    mockFetchSuccess(stateAtStep3)
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('retro-content')).toBeInTheDocument()
    })

    await waitFor(() => {
      const state = useAssistantStore.getState()
      expect(state.current?.stepId).toBe(3)
    })

    const state = useAssistantStore.getState()
    // Stale current is replaced
    expect(state.current?.publicText).toBe('Now on step 3.')
    // Stale history is replaced
    expect(state.history).toHaveLength(2)
    expect(state.history.at(0)?.stepId).toBe(2)
    expect(state.history.at(1)?.stepId).toBe(1)
    // Stale coaching note is replaced
    expect(state.facilitatorCoachingNote).toBeNull()
  })

  it('late join: empty store is hydrated from authoritative server state with history', async () => {
    // Store starts empty (as for a brand-new client joining an in-progress room)
    expect(useAssistantStore.getState().current).toBeNull()
    expect(useAssistantStore.getState().history).toHaveLength(0)

    // Server returns room state at step 3 with 2 history items
    mockFetchSuccess(stateAtStep3)
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('retro-content')).toBeInTheDocument()
    })

    await waitFor(() => {
      const state = useAssistantStore.getState()
      expect(state.current?.stepId).toBe(3)
    })

    const state = useAssistantStore.getState()
    // Current message from server
    expect(state.current?.publicText).toBe('Now on step 3.')
    // History from server — 2 items, newest-first
    expect(state.history).toHaveLength(2)
    expect(state.history.at(0)?.stepId).toBe(2)
    expect(state.history.at(1)?.stepId).toBe(1)
  })

  it('late join: GuidanceSidebar renders room history items from server bootstrap', async () => {
    // Server returns room state at step 3 with 2 history items
    mockFetchSuccess(stateAtStep3)
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('guidance-content')).toBeInTheDocument()
    })

    // Current message displayed
    await waitFor(() => {
      expect(screen.getByTestId('guidance-content')).toHaveTextContent('Now on step 3.')
    })

    // History items displayed in sidebar (newest-first)
    const historyList = screen.getByTestId('assistant-history-list')
    await waitFor(() => {
      expect(historyList.querySelectorAll(':scope > div')).toHaveLength(2)
    })
    expect(historyList).toHaveTextContent('Was on step 2.')
    expect(historyList).toHaveTextContent('Was on step 1.')
  })

  it('no duplicate: bootstrap via refetch after STEP_ADVANCED does not double-append history', async () => {
    // Initial load at step 3
    let currentServerState = stateAtStep3
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = input instanceof Request ? input.url : String(input)
      if (url.match(/\/retros\/[^/]+$/)) {
        return Promise.resolve(new Response(JSON.stringify(currentServerState), { status: 200, headers: { 'Content-Type': 'application/json' } }))
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

    // Wait for initial bootstrap
    await waitFor(() => {
      expect(useAssistantStore.getState().current?.stepId).toBe(3)
    })

    // Server advances to step 4
    currentServerState = stateAtStep4

    // SSE fires STEP_ADVANCED — triggers refetch and second bootstrap
    act(() => {
      capturedSSE.handlers[EventType.STEP_ADVANCED]?.('null')
    })

    // Wait for the re-fetched state to bootstrap
    await waitFor(() => {
      expect(useAssistantStore.getState().current?.stepId).toBe(4)
    })

    const state = useAssistantStore.getState()
    // Current is the new step
    expect(state.current?.publicText).toBe('Now on step 4.')
    // History matches server exactly — 3 items, no duplicates
    expect(state.history).toHaveLength(3)
    expect(state.history.at(0)?.stepId).toBe(3)
    expect(state.history.at(1)?.stepId).toBe(2)
    expect(state.history.at(2)?.stepId).toBe(1)
    // No old current (step 3) duplicated in history as index 0 AND 1
    const step3Occurrences = state.history.filter((m) => m.stepId === 3)
    expect(step3Occurrences).toHaveLength(1)
  })

  it('no duplicate: multiple STEP_ADVANCED refetches do not accumulate stale history', async () => {
    // Simulate a participant who sees 3 SSE-driven refetches in sequence
    const states = [stateAtStep3, stateAtStep4]
    let callCount = 0
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = input instanceof Request ? input.url : String(input)
      if (url.match(/\/retros\/[^/]+$/)) {
        // Return stateAtStep3 on first fetch, stateAtStep4 on subsequent fetches
        const s = callCount === 0 ? states[0]! : states[1]!
        callCount++
        return Promise.resolve(new Response(JSON.stringify(s), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      if (url.includes('/participants')) {
        return Promise.resolve(new Response(JSON.stringify(baseParticipants), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      return Promise.resolve(new Response('{}', { status: 404 }))
    })

    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(useAssistantStore.getState().current?.stepId).toBe(3)
    })

    // Trigger STEP_ADVANCED — server now returns step 4 state
    act(() => {
      capturedSSE.handlers[EventType.STEP_ADVANCED]?.('null')
    })

    await waitFor(() => {
      expect(useAssistantStore.getState().current?.stepId).toBe(4)
    })

    const state = useAssistantStore.getState()
    // Final state is exactly what the server said — no accumulation
    expect(state.history).toHaveLength(3)
    expect(state.history.at(0)?.stepId).toBe(3)
    expect(state.history.at(1)?.stepId).toBe(2)
    expect(state.history.at(2)?.stepId).toBe(1)
  })
})

describe('RetroPage guidance-sidebar coachmark integration', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('renders guidance-sidebar-coachmark when a step is active', async () => {
    mockFetchSuccess()
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('guidance-sidebar-coachmark')).toBeInTheDocument()
    })
  })

  it('does NOT render guidance-sidebar-coachmark in the lobby (no active step)', async () => {
    const lobbyState = {
      ...baseState,
      phase: 'LOBBY',
      currentStepId: null,
      currentStepIndex: 0,
      steps: [],
    }
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = input instanceof Request ? input.url : String(input)
      if (url.match(/\/retros\/[^/]+$/)) {
        return Promise.resolve(new Response(JSON.stringify(lobbyState), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      if (url.includes('/participants')) {
        return Promise.resolve(new Response(JSON.stringify(baseParticipants), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      return Promise.resolve(new Response('{}', { status: 404 }))
    })

    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('retro-id-display')).toBeInTheDocument()
    })

    expect(screen.queryByTestId('guidance-sidebar-coachmark')).not.toBeInTheDocument()
  })

  it('coachmark disappears after clicking the close button', async () => {
    const user = userEvent.setup()
    mockFetchSuccess()
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('guidance-sidebar-coachmark')).toBeInTheDocument()
    })

    await user.click(screen.getByTestId('guidance-sidebar-coachmark-close'))

    expect(screen.queryByTestId('guidance-sidebar-coachmark')).not.toBeInTheDocument()
  })

  it('coachmark shows expected tip text', async () => {
    mockFetchSuccess()
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('guidance-sidebar-coachmark-content')).toBeInTheDocument()
    })

    expect(screen.getByTestId('guidance-sidebar-coachmark-content')).toHaveTextContent(
      'The Virtual Facilitator guides you step by step. Dismiss when ready.'
    )
  })
})

describe('RetroPage next-step coachmark (facilitator anchor)', () => {
  const facilitatorState = { ...baseState, isFacilitator: true }
  const participantState = { ...baseState, isFacilitator: false }

  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('renders next-step-coachmark for facilitator when a step is active', async () => {
    mockFetchSuccess(facilitatorState)
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('next-step-coachmark')).toBeInTheDocument()
    })
  })

  it('does NOT render next-step-coachmark for non-facilitator', async () => {
    mockFetchSuccess(participantState)
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('retro-content')).toBeInTheDocument()
    })

    expect(screen.queryByTestId('next-step-coachmark')).not.toBeInTheDocument()
  })

  it('does NOT render next-step-coachmark in lobby (no active step)', async () => {
    const lobbyState = {
      ...facilitatorState,
      phase: 'LOBBY',
      currentStepId: null,
      currentStepIndex: 0,
      steps: [],
    }
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = input instanceof Request ? input.url : String(input)
      if (url.match(/\/retros\/[^/]+$/)) {
        return Promise.resolve(new Response(JSON.stringify(lobbyState), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      if (url.includes('/participants')) {
        return Promise.resolve(new Response(JSON.stringify(baseParticipants), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      return Promise.resolve(new Response('{}', { status: 404 }))
    })

    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('retro-id-display')).toBeInTheDocument()
    })

    expect(screen.queryByTestId('next-step-coachmark')).not.toBeInTheDocument()
  })

  it('next-step-coachmark disappears after clicking close', async () => {
    const user = userEvent.setup()
    mockFetchSuccess(facilitatorState)
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('next-step-coachmark')).toBeInTheDocument()
    })

    await user.click(screen.getByTestId('next-step-coachmark-close'))

    expect(screen.queryByTestId('next-step-coachmark')).not.toBeInTheDocument()
  })

  it('next-step-coachmark shows actionable facilitator copy', async () => {
    mockFetchSuccess(facilitatorState)
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('next-step-coachmark-content')).toBeInTheDocument()
    })

    expect(screen.getByTestId('next-step-coachmark-content')).toHaveTextContent(
      'When the room has shared their thoughts, click Next to advance everyone to the next step.'
    )
  })

  it('Next button carries data-coachmark="next-step" anchor attribute for facilitator', async () => {
    mockFetchSuccess(facilitatorState)
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('next-step-button')).toBeInTheDocument()
    })

    expect(screen.getByTestId('next-step-button')).toHaveAttribute('data-coachmark', 'next-step')
  })
})

describe('RetroPage note-input coachmark (board anchor)', () => {
  const boardState = {
    ...baseState,
    isFacilitator: false,
    steps: [
      {
        id: 1,
        title: 'Mad Sad Glad',
        componentType: 'MULTI_COLUMN_BOARD',
        advancementTrigger: 'FACILITATOR_CLICK',
        durationSeconds: null,
        componentConfig: {},
        guidance: null,
      },
    ],
  }

  const nonBoardState = {
    ...baseState,
    isFacilitator: false,
    steps: [
      {
        id: 1,
        title: 'Rating Step',
        componentType: 'RATING_SCALE',
        advancementTrigger: 'FACILITATOR_CLICK',
        durationSeconds: null,
        componentConfig: {},
        guidance: null,
      },
    ],
  }

  let noteInputAnchor: HTMLElement | null = null

  beforeEach(() => {
    vi.restoreAllMocks()
    noteInputAnchor = document.createElement('div')
    noteInputAnchor.setAttribute('data-coachmark', 'note-input')
    noteInputAnchor.style.cssText = 'position:fixed;top:300px;left:400px;width:200px;height:40px'
    document.body.appendChild(noteInputAnchor)
  })

  afterEach(() => {
    noteInputAnchor?.remove()
    noteInputAnchor = null
  })

  it('renders note-input-coachmark when componentType is MULTI_COLUMN_BOARD', async () => {
    mockFetchSuccess(boardState)
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('note-input-coachmark')).toBeInTheDocument()
    })
  })

  it('does NOT render note-input-coachmark when componentType is not MULTI_COLUMN_BOARD', async () => {
    mockFetchSuccess(nonBoardState)
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('retro-content')).toBeInTheDocument()
    })

    expect(screen.queryByTestId('note-input-coachmark')).not.toBeInTheDocument()
  })

  it('does NOT render note-input-coachmark in lobby (no active step)', async () => {
    const lobbyState = {
      ...boardState,
      phase: 'LOBBY',
      currentStepId: null,
      currentStepIndex: 0,
      steps: [],
    }
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = input instanceof Request ? input.url : String(input)
      if (url.match(/\/retros\/[^/]+$/)) {
        return Promise.resolve(new Response(JSON.stringify(lobbyState), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      if (url.includes('/participants')) {
        return Promise.resolve(new Response(JSON.stringify(baseParticipants), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      return Promise.resolve(new Response('{}', { status: 404 }))
    })

    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('retro-id-display')).toBeInTheDocument()
    })

    expect(screen.queryByTestId('note-input-coachmark')).not.toBeInTheDocument()
  })

  it('note-input-coachmark disappears after clicking close', async () => {
    const user = userEvent.setup()
    mockFetchSuccess(boardState)
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('note-input-coachmark')).toBeInTheDocument()
    })

    await user.click(screen.getByTestId('note-input-coachmark-close'))

    expect(screen.queryByTestId('note-input-coachmark')).not.toBeInTheDocument()
  })

  it('note-input-coachmark shows actionable participant copy', async () => {
    mockFetchSuccess(boardState)
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('note-input-coachmark-content')).toBeInTheDocument()
    })

    expect(screen.getByTestId('note-input-coachmark-content')).toHaveTextContent(
      'Type your note and press Enter or click ➕ to add it to the board.'
    )
  })

  it('facilitator also sees note-input-coachmark on MULTI_COLUMN_BOARD steps', async () => {
    mockFetchSuccess({ ...boardState, isFacilitator: true })
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('note-input-coachmark')).toBeInTheDocument()
    })
  })
})

describe('RetroPage quickActions wiring (task 14)', () => {
  const facilitatorActiveState = { ...baseState, isFacilitator: true }
  const participantActiveState = { ...baseState, isFacilitator: false }
  const facilitatorLobbyState = {
    ...baseState,
    isFacilitator: true,
    phase: 'LOBBY',
    currentStepId: null,
    currentStepIndex: 0,
    steps: [],
  }

  function mockWithState(state: object) {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = input instanceof Request ? input.url : String(input)
      if (url.match(/\/retros\/[^/]+$/)) {
        return Promise.resolve(new Response(JSON.stringify(state), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      if (url.includes('/participants')) {
        return Promise.resolve(new Response(JSON.stringify(baseParticipants), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      }
      return Promise.resolve(new Response('{}', { status: 404 }))
    })
  }

  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('facilitator on active step sees the quick-action advance-step button', async () => {
    mockWithState(facilitatorActiveState)
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('quick-action-advance-step')).toBeInTheDocument()
    })
  })

  it('participant on active step does NOT see the quick-action advance-step button', async () => {
    mockWithState(participantActiveState)
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('retro-content')).toBeInTheDocument()
    })

    expect(screen.queryByTestId('quick-action-advance-step')).not.toBeInTheDocument()
    expect(screen.getByTestId('facilitator-quick-actions-placeholder')).toBeInTheDocument()
  })

  it('facilitator in lobby (no currentStep) does NOT see the quick-action advance-step button', async () => {
    mockWithState(facilitatorLobbyState)
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('retro-id-display')).toBeInTheDocument()
    })

    expect(screen.queryByTestId('quick-action-advance-step')).not.toBeInTheDocument()
  })

  it('quick-action advance-step button is secondary to the main guidance content (not inside guidance-content)', async () => {
    mockWithState(facilitatorActiveState)
    renderRetroPage(buildQueryClient())

    await waitFor(() => {
      expect(screen.getByTestId('quick-action-advance-step')).toBeInTheDocument()
    })

    const guidanceContent = screen.getByTestId('guidance-content')
    const quickActionBtn = screen.getByTestId('quick-action-advance-step')
    expect(guidanceContent.contains(quickActionBtn)).toBe(false)
  })
})
