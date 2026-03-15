import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useTimer } from './useRetro'

vi.mock('@/lib/api-client', () => ({
  apiClient: { GET: vi.fn() },
  ApiError: class ApiError extends Error {
    status: number
    constructor(status: number, message: string) {
      super(message)
      this.status = status
    }
  },
}))

import { apiClient } from '@/lib/api-client'

function makeWrapper() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children)
}

describe('useTimer', () => {
  beforeEach(() => {
    vi.clearAllMocks()
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
})
