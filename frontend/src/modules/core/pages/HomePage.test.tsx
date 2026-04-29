import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { vi } from 'vitest'
import HomePage from '@/modules/core/pages/HomePage'

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>()
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  }
})

beforeEach(() => {
  mockNavigate.mockReset()
  vi.restoreAllMocks()
})

describe('HomePage', () => {
  it('renders the welcome heading', () => {
    render(
      <MemoryRouter>
        <HomePage />
      </MemoryRouter>
    )
    expect(screen.getByRole('heading', { name: 'Welcome to Reflect.Direct' })).toBeInTheDocument()
  })

  it('renders the welcome text', () => {
    render(
      <MemoryRouter>
        <HomePage />
      </MemoryRouter>
    )
    expect(screen.getByText('Welcome to Reflect.Direct')).toBeInTheDocument()
  })

  it('navigates to redirectUrl from JSON body after create', async () => {
    const retroId = 'abc-123'
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({ retroId, redirectUrl: `/retro/${retroId}`, sessionName: 'My Retro' }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      )
    )

    render(
      <MemoryRouter>
        <HomePage />
      </MemoryRouter>
    )

    fireEvent.change(screen.getByPlaceholderText('Session name'), { target: { value: 'My Retro' } })
    fireEvent.click(screen.getByRole('button', { name: 'Create Session' }))

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith(`/retro/${retroId}`)
    })
  })

  it('shows error when create returns non-ok status', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(null, { status: 500 })
    )

    render(
      <MemoryRouter>
        <HomePage />
      </MemoryRouter>
    )

    fireEvent.change(screen.getByPlaceholderText('Session name'), { target: { value: 'Bad Retro' } })
    fireEvent.click(screen.getByRole('button', { name: 'Create Session' }))

    await waitFor(() => {
      expect(screen.getByText('Failed to create session')).toBeInTheDocument()
    })
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it('navigates to redirectUrl from JSON body after join', async () => {
    const retroId = 'xyz-456'
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(
        JSON.stringify({ retroId, redirectUrl: `/retro/${retroId}` }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      )
    )

    render(
      <MemoryRouter>
        <HomePage />
      </MemoryRouter>
    )

    fireEvent.change(screen.getByPlaceholderText('Session ID'), { target: { value: retroId } })
    fireEvent.click(screen.getByRole('button', { name: 'Join Session' }))

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith(`/retro/${retroId}`)
    })
  })

  it('shows error when join returns non-ok status', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(null, { status: 404 })
    )

    render(
      <MemoryRouter>
        <HomePage />
      </MemoryRouter>
    )

    fireEvent.change(screen.getByPlaceholderText('Session ID'), { target: { value: 'bad-id' } })
    fireEvent.click(screen.getByRole('button', { name: 'Join Session' }))

    await waitFor(() => {
      expect(screen.getByText('Failed to join session')).toBeInTheDocument()
    })
    expect(mockNavigate).not.toHaveBeenCalled()
  })
})
