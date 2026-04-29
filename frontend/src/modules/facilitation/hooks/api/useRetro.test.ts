import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useAppliedVersionStore, useParticipants, useRetroState, useTimer } from '@/modules/facilitation/hooks/api/useRetro'
import { fetchActionItems } from '@/modules/facilitation/hooks/api/useActionItems'
import { fetchEscalations } from '@/modules/facilitation/hooks/api/useEscalations'

vi.mock('@/shared/lib/api-client', () => ({
  apiClient: { GET: vi.fn() },
  ApiError: class ApiError extends Error {
    status: number
    constructor(status: number, message: string) {
      super(message)
      this.status = status
    }
  },
}))

import { apiClient } from '@/shared/lib/api-client'

function makeWrapper() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children)
}

describe('useTimer', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAppliedVersionStore.setState({ appliedVersionByRetroId: {} })
  })

  it('returns TimerStateDto on 200', async () => {
    vi.mocked(apiClient.GET).mockResolvedValueOnce({
      data: { remainingSeconds: 90, isPaused: false, state: 'yellow' },
      response: { ok: true, status: 200 } as Response,
    })
    const { result } = renderHook(() => useTimer('retro-abc'), { wrapper: makeWrapper() })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual({ remainingSeconds: 90, isPaused: false, state: 'yellow' })
  })

  it('returns null on 204', async () => {
    vi.mocked(apiClient.GET).mockResolvedValueOnce({
      data: undefined,
      response: { ok: true, status: 204 } as Response,
    })
    const { result } = renderHook(() => useTimer('retro-abc'), { wrapper: makeWrapper() })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toBeNull()
  })

  it('is disabled when retroId is undefined', () => {
    const { result } = renderHook(() => useTimer(undefined), { wrapper: makeWrapper() })
    expect(result.current.fetchStatus).toBe('idle')
  })

  it('advances applied version on retro state success', async () => {
    vi.mocked(apiClient.GET).mockResolvedValueOnce({
      data: { retroId: 'retro-abc', syncVersion: 4 },
      response: { ok: true, status: 200 } as Response,
    })

    const { result } = renderHook(() => useRetroState('retro-abc'), { wrapper: makeWrapper() })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(useAppliedVersionStore.getState().appliedVersionByRetroId['retro-abc']).toBe(4)
  })

  it('advances applied version on participants success', async () => {
    vi.mocked(apiClient.GET).mockResolvedValueOnce({
      data: { syncVersion: 6, data: [{ participantId: 'p1', displayName: 'Alice', role: 'PARTICIPANT' }] },
      response: { ok: true, status: 200 } as Response,
    })

    const { result } = renderHook(() => useParticipants('retro-abc'), { wrapper: makeWrapper() })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(useAppliedVersionStore.getState().appliedVersionByRetroId['retro-abc']).toBe(6)
  })

  it('advances applied version on timer success', async () => {
    vi.mocked(apiClient.GET).mockResolvedValueOnce({
      data: { syncVersion: 7, data: { remainingSeconds: 90, isPaused: false, state: 'yellow' } },
      response: { ok: true, status: 200 } as Response,
    })

    const { result } = renderHook(() => useTimer('retro-abc'), { wrapper: makeWrapper() })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(useAppliedVersionStore.getState().appliedVersionByRetroId['retro-abc']).toBe(7)
  })
})

describe('authoritative fetch helpers', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAppliedVersionStore.setState({ appliedVersionByRetroId: {} })
  })

  it('advances applied version on action items success', async () => {
    vi.mocked(apiClient.GET).mockResolvedValueOnce({
      data: { syncVersion: 8, data: [{ id: 'a1', what: 'Ship it' }] },
      response: { ok: true, status: 200 } as Response,
    })

    await fetchActionItems('retro-abc')

    expect(useAppliedVersionStore.getState().appliedVersionByRetroId['retro-abc']).toBe(8)
  })

  it('advances applied version on escalations success', async () => {
    vi.mocked(apiClient.GET).mockResolvedValueOnce({
      data: { syncVersion: 9, data: [{ id: 'e1', problemDescription: 'Blocked' }] },
      response: { ok: true, status: 200 } as Response,
    })

    await fetchEscalations('retro-abc')

    expect(useAppliedVersionStore.getState().appliedVersionByRetroId['retro-abc']).toBe(9)
  })
})
