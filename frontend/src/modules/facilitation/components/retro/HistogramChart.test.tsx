import { render, screen, act, waitFor } from '@testing-library/react'
import { useContext, useEffect, useRef } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { SSEProvider, SSEContext } from '@/shared/contexts/SSEContext'
import { EventType } from '@/shared/types/events'
import { HistogramChart } from '@/modules/facilitation/components/retro/HistogramChart'

type Dispatch = (eventType: EventType, rawData: string) => void

function DispatchCapture({ holder }: { holder: { current: Dispatch | null } }) {
  const ctx = useContext(SSEContext)
  const holderRef = useRef(holder)
  useEffect(() => {
    holderRef.current.current = ctx?.dispatch ?? null
  })
  return null
}

function buildQueryClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } })
}

const RETRO_ID = 'retro-hist'
const STEP_ID = 3

const MINIMAL_CONFIG = {
  min: 1,
  max: 5,
  step: 1,
  labels: ['Low', 'High'],
}

const NO_RESPONSES: never[] = []

const ONE_RESPONSE = [
  {
    id: 'resp-1',
    participantId: 'p1',
    participantName: 'Alice',
    rating: 4,
    comment: null,
    visible: true,
  },
]

const TWO_RESPONSES = [
  {
    id: 'resp-1',
    participantId: 'p1',
    participantName: 'Alice',
    rating: 4,
    comment: null,
    visible: true,
  },
  {
    id: 'resp-2',
    participantId: 'p2',
    participantName: 'Bob',
    rating: 2,
    comment: null,
    visible: true,
  },
]

type RatingPayload = typeof NO_RESPONSES | typeof ONE_RESPONSE | typeof TWO_RESPONSES

function mockFetchSequential(first: RatingPayload, second: RatingPayload) {
  let callCount = 0
  vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
    const url = input instanceof Request ? input.url : String(input)
    if (url.includes('/response/rating')) {
      callCount++
      const payload = callCount === 1 ? first : second
      return Promise.resolve(
        new Response(JSON.stringify(payload), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        })
      )
    }
    return Promise.resolve(new Response('{}', { status: 404 }))
  })
}

function mockFetchWithSpy(payload: RatingPayload): ReturnType<typeof vi.fn> {
  const fetchSpy = vi.fn().mockResolvedValue(
    new Response(JSON.stringify(payload), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    })
  )
  vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
    const url = input instanceof Request ? input.url : String(input)
    if (url.includes('/response/rating')) return fetchSpy(url) as Promise<Response>
    return Promise.resolve(new Response('{}', { status: 404 }))
  })
  return fetchSpy
}

function renderChart(queryClient: QueryClient, dispatch: { current: Dispatch | null }) {
  return render(
    <QueryClientProvider client={queryClient}>
      <SSEProvider>
        <DispatchCapture holder={dispatch} />
        <HistogramChart
          retroId={RETRO_ID}
          stepId={STEP_ID}
          componentConfig={MINIMAL_CONFIG as Record<string, unknown>}
        />
      </SSEProvider>
    </QueryClientProvider>
  )
}

beforeEach(() => {
  vi.restoreAllMocks()
})

describe('HistogramChart SSE invalidation', () => {
  it('shows empty state when no responses', async () => {
    mockFetchWithSpy(NO_RESPONSES)
    const dispatch = { current: null as Dispatch | null }
    renderChart(buildQueryClient(), dispatch)

    await waitFor(() => {
      expect(screen.getByText('No responses yet')).toBeInTheDocument()
    })
  })

  it('shows Rating Distribution when responses are present', async () => {
    mockFetchWithSpy(ONE_RESPONSE)
    const dispatch = { current: null as Dispatch | null }
    renderChart(buildQueryClient(), dispatch)

    await waitFor(() => {
      expect(screen.getByText('Rating Distribution')).toBeInTheDocument()
    })
  })

  it('refetches ratingResponses and updates response count when NOTE_ADDED fires', async () => {
    mockFetchSequential(ONE_RESPONSE, TWO_RESPONSES)
    const dispatch = { current: null as Dispatch | null }
    renderChart(buildQueryClient(), dispatch)

    await waitFor(() => {
      expect(screen.getByText('1 response')).toBeInTheDocument()
    })

    act(() => {
      dispatch.current!(EventType.NOTE_ADDED, '{"responseId":"resp-2"}')
    })

    await waitFor(() => {
      expect(screen.getByText('2 responses')).toBeInTheDocument()
    })
  })

  it('refetches ratingResponses when NOTE_UPDATED fires', async () => {
    const fetchSpy = mockFetchWithSpy(ONE_RESPONSE)
    const dispatch = { current: null as Dispatch | null }
    renderChart(buildQueryClient(), dispatch)

    await waitFor(() => expect(fetchSpy).toHaveBeenCalledTimes(1))

    act(() => {
      dispatch.current!(EventType.NOTE_UPDATED, '{"responseId":"resp-1"}')
    })

    await waitFor(() => expect(fetchSpy.mock.calls.length).toBeGreaterThan(1))
  })

  it('does NOT refetch ratingResponses when NOTE_DELETED fires (not subscribed)', async () => {
    const fetchSpy = mockFetchWithSpy(ONE_RESPONSE)
    const dispatch = { current: null as Dispatch | null }
    renderChart(buildQueryClient(), dispatch)

    await waitFor(() => expect(fetchSpy).toHaveBeenCalledTimes(1))
    const countAfterMount = fetchSpy.mock.calls.length

    act(() => {
      dispatch.current!(EventType.NOTE_DELETED, '{"responseId":"resp-1"}')
    })
    await Promise.resolve()

    expect(fetchSpy.mock.calls.length).toBe(countAfterMount)
  })

  it('does NOT refetch ratingResponses when VOTE_ADDED fires (not subscribed)', async () => {
    const fetchSpy = mockFetchWithSpy(ONE_RESPONSE)
    const dispatch = { current: null as Dispatch | null }
    renderChart(buildQueryClient(), dispatch)

    await waitFor(() => expect(fetchSpy).toHaveBeenCalledTimes(1))
    const countAfterMount = fetchSpy.mock.calls.length

    act(() => {
      dispatch.current!(EventType.VOTE_ADDED, '{"responseId":"resp-1"}')
    })
    await Promise.resolve()

    expect(fetchSpy.mock.calls.length).toBe(countAfterMount)
  })
})
