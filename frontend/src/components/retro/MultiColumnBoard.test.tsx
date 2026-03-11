import { render, screen, act, waitFor } from '@testing-library/react'
import { useContext, useEffect, useRef } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { SSEProvider, SSEContext } from '@/contexts/SSEContext'
import { EventType } from '@/types/events'
import { MultiColumnBoard } from './MultiColumnBoard'

vi.mock('@dnd-kit/core', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@dnd-kit/core')>()
  return {
    ...actual,
    DndContext: ({ children }: { children: React.ReactNode }) => <>{children}</>,
    useDroppable: () => ({ setNodeRef: () => undefined, isOver: false }),
    PointerSensor: actual.PointerSensor,
    useSensor: actual.useSensor,
    useSensors: actual.useSensors,
    closestCenter: actual.closestCenter,
  }
})

vi.mock('@dnd-kit/sortable', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@dnd-kit/sortable')>()
  return {
    ...actual,
    SortableContext: ({ children }: { children: React.ReactNode }) => <>{children}</>,
    useSortable: () => ({
      attributes: {},
      listeners: {},
      setNodeRef: () => undefined,
      transform: null,
      transition: undefined,
      isDragging: false,
    }),
  }
})

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

const RETRO_ID = 'retro-abc'
const STEP_ID = 1

const MINIMAL_CONFIG = {
  columns: [
    { id: 'col1', title: 'Mad', color: '#EF4444' },
    { id: 'col2', title: 'Glad', color: '#10B981' },
  ],
  capabilities: { allowInput: false },
}

const EMPTY_CLUSTERS = { clustered: {}, unclustered: [] }

const ONE_NOTE_CLUSTERS = {
  clustered: {},
  unclustered: [
    {
      id: 'note-1',
      columnId: 'col1',
      content: 'Something mad',
      visible: true,
      participantName: 'Alice',
      participantId: 'p1',
      voteCount: 0,
    },
  ],
}

const TWO_NOTE_CLUSTERS = {
  clustered: {},
  unclustered: [
    {
      id: 'note-1',
      columnId: 'col1',
      content: 'Something mad',
      visible: true,
      participantName: 'Alice',
      participantId: 'p1',
      voteCount: 0,
    },
    {
      id: 'note-2',
      columnId: 'col1',
      content: 'Another mad note',
      visible: true,
      participantName: 'Bob',
      participantId: 'p2',
      voteCount: 0,
    },
  ],
}

type ClustersPayload = typeof EMPTY_CLUSTERS | typeof ONE_NOTE_CLUSTERS | typeof TWO_NOTE_CLUSTERS

function mockFetchSequential(first: ClustersPayload, second: ClustersPayload) {
  let callCount = 0
  vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
    const url = input instanceof Request ? input.url : String(input)
    if (url.includes('/clusters')) {
      callCount++
      const payload = callCount === 1 ? first : second
      return Promise.resolve(
        new Response(JSON.stringify(payload), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        })
      )
    }
    if (url.includes('/api/me')) {
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
    }
    return Promise.resolve(new Response('{}', { status: 404 }))
  })
}

function mockFetchWithSpy(payload: ClustersPayload): ReturnType<typeof vi.fn> {
  const fetchSpy = vi.fn().mockResolvedValue(
    new Response(JSON.stringify(payload), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    })
  )
  vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
    const url = input instanceof Request ? input.url : String(input)
    if (url.includes('/clusters')) return fetchSpy(url) as Promise<Response>
    if (url.includes('/api/me'))
      return Promise.resolve(
        new Response(
          JSON.stringify({ isAuthenticated: false, isGuest: true, authType: 'GUEST' }),
          { status: 200, headers: { 'Content-Type': 'application/json' } }
        )
      )
    return Promise.resolve(new Response('{}', { status: 404 }))
  })
  return fetchSpy
}

function renderBoard(queryClient: QueryClient, dispatch: { current: Dispatch | null }) {
  return render(
    <QueryClientProvider client={queryClient}>
      <SSEProvider>
        <DispatchCapture holder={dispatch} />
        <MultiColumnBoard
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

describe('MultiColumnBoard SSE invalidation', () => {
  it('shows initial note from first fetch', async () => {
    mockFetchSequential(ONE_NOTE_CLUSTERS, ONE_NOTE_CLUSTERS)
    const dispatch = { current: null as Dispatch | null }
    renderBoard(buildQueryClient(), dispatch)

    await waitFor(() => {
      expect(screen.getByText('Something mad')).toBeInTheDocument()
    })
  })

  it('refetches clusters and updates UI when NOTE_ADDED fires', async () => {
    mockFetchSequential(ONE_NOTE_CLUSTERS, TWO_NOTE_CLUSTERS)
    const dispatch = { current: null as Dispatch | null }
    renderBoard(buildQueryClient(), dispatch)

    await waitFor(() => {
      expect(screen.getByText('Something mad')).toBeInTheDocument()
    })

    act(() => {
      dispatch.current!(EventType.NOTE_ADDED, '{"responseId":"note-2"}')
    })

    await waitFor(() => {
      expect(screen.getByText('Another mad note')).toBeInTheDocument()
    })
  })

  it('refetches clusters when NOTE_UPDATED fires', async () => {
    const fetchSpy = mockFetchWithSpy(ONE_NOTE_CLUSTERS)
    const dispatch = { current: null as Dispatch | null }
    renderBoard(buildQueryClient(), dispatch)

    await waitFor(() => expect(fetchSpy).toHaveBeenCalledTimes(1))

    act(() => {
      dispatch.current!(EventType.NOTE_UPDATED, '{"responseId":"note-1"}')
    })

    await waitFor(() => expect(fetchSpy.mock.calls.length).toBeGreaterThan(1))
  })

  it('refetches clusters when NOTE_DELETED fires', async () => {
    const fetchSpy = mockFetchWithSpy(EMPTY_CLUSTERS)
    const dispatch = { current: null as Dispatch | null }
    renderBoard(buildQueryClient(), dispatch)

    await waitFor(() => expect(fetchSpy).toHaveBeenCalledTimes(1))

    act(() => {
      dispatch.current!(EventType.NOTE_DELETED, '{"responseId":"note-1"}')
    })

    await waitFor(() => expect(fetchSpy.mock.calls.length).toBeGreaterThan(1))
  })

  it('refetches clusters when VOTE_ADDED fires', async () => {
    const fetchSpy = mockFetchWithSpy(ONE_NOTE_CLUSTERS)
    const dispatch = { current: null as Dispatch | null }
    renderBoard(buildQueryClient(), dispatch)

    await waitFor(() => expect(fetchSpy).toHaveBeenCalledTimes(1))

    act(() => {
      dispatch.current!(EventType.VOTE_ADDED, '{"responseId":"note-1"}')
    })

    await waitFor(() => expect(fetchSpy.mock.calls.length).toBeGreaterThan(1))
  })

  it('refetches clusters when VOTE_REMOVED fires', async () => {
    const fetchSpy = mockFetchWithSpy(ONE_NOTE_CLUSTERS)
    const dispatch = { current: null as Dispatch | null }
    renderBoard(buildQueryClient(), dispatch)

    await waitFor(() => expect(fetchSpy).toHaveBeenCalledTimes(1))

    act(() => {
      dispatch.current!(EventType.VOTE_REMOVED, '{"responseId":"note-1"}')
    })

    await waitFor(() => expect(fetchSpy.mock.calls.length).toBeGreaterThan(1))
  })

  it('does NOT trigger a clusters refetch when an unsubscribed event fires', async () => {
    const fetchSpy = mockFetchWithSpy(EMPTY_CLUSTERS)
    const dispatch = { current: null as Dispatch | null }
    renderBoard(buildQueryClient(), dispatch)

    await waitFor(() => expect(fetchSpy).toHaveBeenCalledTimes(1))
    const countAfterMount = fetchSpy.mock.calls.length

    act(() => {
      dispatch.current!(EventType.STEP_ADVANCED, '"refresh"')
    })
    await Promise.resolve()

    expect(fetchSpy.mock.calls.length).toBe(countAfterMount)
  })
})

describe('MultiColumnBoard note editing', () => {
  const OWN_NOTE_CLUSTERS = {
    clustered: {},
    unclustered: [
      {
        id: 'note-own',
        columnId: 'col1',
        content: 'My own frustration',
        visible: true,
        participantName: 'Alice',
        participantId: 'p1',
        voteCount: 0,
      },
    ],
  }

  const UPDATED_NOTE_CLUSTERS = {
    clustered: {},
    unclustered: [
      {
        id: 'note-own',
        columnId: 'col1',
        content: 'Updated frustration with more details',
        visible: true,
        participantName: 'Alice',
        participantId: 'p1',
        voteCount: 0,
      },
    ],
  }

  const MINIMAL_CONFIG_WITH_INPUT = {
    columns: [
      { id: 'col1', title: 'Mad', color: '#EF4444' },
    ],
    capabilities: { allowInput: false },
  }

  function mockFetchWithMeAsP1(first: typeof OWN_NOTE_CLUSTERS, second?: typeof OWN_NOTE_CLUSTERS | typeof UPDATED_NOTE_CLUSTERS) {
    let callCount = 0
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = input instanceof Request ? input.url : String(input)
      if (url.includes('/clusters')) {
        callCount++
        const payload = second && callCount > 1 ? second : first
        return Promise.resolve(
          new Response(JSON.stringify(payload), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          })
        )
      }
      if (url.includes('/api/me')) {
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
      }
      if (url.includes('/response/') && !url.includes('/clusters')) {
        return Promise.resolve(new Response('{}', { status: 200 }))
      }
      return Promise.resolve(new Response('{}', { status: 404 }))
    })
  }

  function renderBoardWithConfig(queryClient: QueryClient, dispatch: { current: Dispatch | null }) {
    return render(
      <QueryClientProvider client={queryClient}>
        <SSEProvider>
          <DispatchCapture holder={dispatch} />
          <MultiColumnBoard
            retroId={RETRO_ID}
            stepId={STEP_ID}
            componentConfig={MINIMAL_CONFIG_WITH_INPUT as Record<string, unknown>}
          />
        </SSEProvider>
      </QueryClientProvider>
    )
  }

  it('renders edit affordance (click-to-edit) for own notes', async () => {
    mockFetchWithMeAsP1(OWN_NOTE_CLUSTERS)
    const dispatch = { current: null as Dispatch | null }
    renderBoardWithConfig(buildQueryClient(), dispatch)

    await waitFor(() => {
      expect(screen.getByText('My own frustration')).toBeInTheDocument()
    })

    // Own notes have cursor-pointer and title="Click to edit"
    const noteText = screen.getByTitle('Click to edit')
    expect(noteText).toBeInTheDocument()
    expect(noteText).toHaveTextContent('My own frustration')
  })

  it('activates inline edit mode when own note text is clicked', async () => {
    mockFetchWithMeAsP1(OWN_NOTE_CLUSTERS)
    const dispatch = { current: null as Dispatch | null }
    const user = (await import('@testing-library/user-event')).default.setup()
    renderBoardWithConfig(buildQueryClient(), dispatch)

    await waitFor(() => {
      expect(screen.getByText('My own frustration')).toBeInTheDocument()
    })

    const noteText = screen.getByTitle('Click to edit')
    await user.click(noteText)

    await waitFor(() => {
      expect(screen.getByDisplayValue('My own frustration')).toBeInTheDocument()
    })
  })

  it('submits updated content when Enter is pressed in edit textarea', async () => {
    mockFetchWithMeAsP1(OWN_NOTE_CLUSTERS, UPDATED_NOTE_CLUSTERS)
    const dispatch = { current: null as Dispatch | null }
    const user = (await import('@testing-library/user-event')).default.setup()
    renderBoardWithConfig(buildQueryClient(), dispatch)

    await waitFor(() => {
      expect(screen.getByText('My own frustration')).toBeInTheDocument()
    })

    const noteText = screen.getByTitle('Click to edit')
    await user.click(noteText)

    await waitFor(() => {
      expect(screen.getByDisplayValue('My own frustration')).toBeInTheDocument()
    })

    const textarea = screen.getByDisplayValue('My own frustration')
    await user.clear(textarea)
    await user.type(textarea, 'Updated frustration with more details')
    await user.keyboard('{Enter}')

    await waitFor(() => {
      expect(screen.getByText('Updated frustration with more details')).toBeInTheDocument()
    })
    expect(screen.queryByText('My own frustration')).not.toBeInTheDocument()
  })
})
