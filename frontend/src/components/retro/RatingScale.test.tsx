import { render, screen, act, waitFor } from '@testing-library/react'
import { useContext, useEffect, useRef } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { SSEProvider, SSEContext } from '@/contexts/SSEContext'
import { EventType } from '@/types/events'
import { RatingScale } from './RatingScale'

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

const RETRO_ID = 'retro-xyz'
const STEP_ID = 2

const MINIMAL_CONFIG = {
  min: 1,
  max: 5,
  step: 1,
  labels: ['Low', 'High'],
  inputType: 'radio',
  allowComment: false,
  commentMaxLength: 500,
}

const NO_RATING_RESPONSE = null

const EXISTING_RATING = {
  id: 'resp-1',
  participantId: 'p1',
  participantName: 'Alice',
  rating: 4,
  comment: null,
  visible: true,
}

function mockFetchWithSpy(ratingPayload: typeof NO_RATING_RESPONSE | typeof EXISTING_RATING): ReturnType<typeof vi.fn> {
  const fetchSpy = vi.fn().mockResolvedValue(
    ratingPayload === null
      ? new Response('', { status: 404 })
      : new Response(JSON.stringify(ratingPayload), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        })
  )
  vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
    const url = String(input)
    if (url.includes('/response/rating/me')) return fetchSpy(url) as Promise<Response>
    if (url.includes('/api/me'))
      return Promise.resolve(
        new Response(
          JSON.stringify({
            isAuthenticated: true,
            isGuest: false,
            authType: 'OIDC',
            user: { id: 'p1', displayName: 'Alice', role: 'USER' },
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } }
        )
      )
    return Promise.resolve(new Response('{}', { status: 404 }))
  })
  return fetchSpy
}

function renderScale(queryClient: QueryClient, dispatch: { current: Dispatch | null }) {
  return render(
    <QueryClientProvider client={queryClient}>
      <SSEProvider>
        <DispatchCapture holder={dispatch} />
        <RatingScale
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

describe('RatingScale SSE invalidation', () => {
  it('renders the rating form when no existing response', async () => {
    mockFetchWithSpy(NO_RATING_RESPONSE)
    const dispatch = { current: null as Dispatch | null }
    renderScale(buildQueryClient(), dispatch)

    await waitFor(() => {
      expect(screen.getByText('Submit My Rating')).toBeInTheDocument()
    })
  })

  it('refetches ratingResponse when NOTE_ADDED fires', async () => {
    const fetchSpy = mockFetchWithSpy(NO_RATING_RESPONSE)
    const dispatch = { current: null as Dispatch | null }
    renderScale(buildQueryClient(), dispatch)

    await waitFor(() => expect(fetchSpy).toHaveBeenCalledTimes(1))

    act(() => {
      dispatch.current!(EventType.NOTE_ADDED, '{"responseId":"r1"}')
    })

    await waitFor(() => expect(fetchSpy.mock.calls.length).toBeGreaterThan(1))
  })

  it('refetches ratingResponse when NOTE_UPDATED fires', async () => {
    const fetchSpy = mockFetchWithSpy(NO_RATING_RESPONSE)
    const dispatch = { current: null as Dispatch | null }
    renderScale(buildQueryClient(), dispatch)

    await waitFor(() => expect(fetchSpy).toHaveBeenCalledTimes(1))

    act(() => {
      dispatch.current!(EventType.NOTE_UPDATED, '{"responseId":"r1"}')
    })

    await waitFor(() => expect(fetchSpy.mock.calls.length).toBeGreaterThan(1))
  })

  it('does NOT refetch ratingResponse when NOTE_DELETED fires (not subscribed)', async () => {
    const fetchSpy = mockFetchWithSpy(NO_RATING_RESPONSE)
    const dispatch = { current: null as Dispatch | null }
    renderScale(buildQueryClient(), dispatch)

    await waitFor(() => expect(fetchSpy).toHaveBeenCalledTimes(1))
    const countAfterMount = fetchSpy.mock.calls.length

    act(() => {
      dispatch.current!(EventType.NOTE_DELETED, '{"responseId":"r1"}')
    })
    await Promise.resolve()

    expect(fetchSpy.mock.calls.length).toBe(countAfterMount)
  })

  it('does NOT refetch ratingResponse when VOTE_ADDED fires (not subscribed)', async () => {
    const fetchSpy = mockFetchWithSpy(NO_RATING_RESPONSE)
    const dispatch = { current: null as Dispatch | null }
    renderScale(buildQueryClient(), dispatch)

    await waitFor(() => expect(fetchSpy).toHaveBeenCalledTimes(1))
    const countAfterMount = fetchSpy.mock.calls.length

    act(() => {
      dispatch.current!(EventType.VOTE_ADDED, '{"responseId":"r1"}')
    })
    await Promise.resolve()

    expect(fetchSpy.mock.calls.length).toBe(countAfterMount)
  })

  it('shows submitted state when an existing rating is loaded', async () => {
    mockFetchWithSpy(EXISTING_RATING)
    const dispatch = { current: null as Dispatch | null }
    renderScale(buildQueryClient(), dispatch)

    await waitFor(() => {
      expect(screen.getByText('Response submitted')).toBeInTheDocument()
    })
  })
})
