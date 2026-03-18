import { render, screen, act, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import RetroPage from './RetroPage'
import MainLayout from '@/layouts/MainLayout'
import { EventType } from '@/types/events'
import { useSSESubscription } from '@/hooks/useSSEContext'
import { useRetroStateStore } from '@/store/retroStore'

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

function renderRetroPageWithLayout(queryClient: QueryClient) {
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/retro/${RETRO_ID}`]}>
        <Routes>
          <Route element={<MainLayout />}>
            <Route path="/retro/:retroId" element={<RetroPage />} />
          </Route>
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
      if (url.includes('/state')) {
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
      if (url.includes('/state')) {
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
      if (url.includes('/state')) {
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
