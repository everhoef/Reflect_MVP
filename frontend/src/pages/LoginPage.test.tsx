import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { vi, type MockedFunction } from 'vitest'
import LoginPage from './LoginPage'

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>()
  return { ...actual, useNavigate: () => mockNavigate }
})

function renderLoginPage() {
  return render(
    <MemoryRouter>
      <LoginPage />
    </MemoryRouter>
  )
}

describe('LoginPage', () => {
  beforeEach(() => {
    mockNavigate.mockReset()
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 401 }))
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('renders the guest login heading', () => {
    renderLoginPage()
    expect(screen.getByRole('heading', { name: 'Join as Guest' })).toBeInTheDocument()
  })

  it('renders the sign in prompt text', () => {
    renderLoginPage()
    expect(screen.getByPlaceholderText('Your display name')).toBeInTheDocument()
  })

  it('redirects to home when already authenticated', async () => {
    ;(globalThis.fetch as MockedFunction<typeof fetch>).mockResolvedValue(
      new Response(JSON.stringify({ isAuthenticated: true }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    )
    renderLoginPage()
    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/', { replace: true })
    })
  })

  it('stays on login when not authenticated', async () => {
    ;(globalThis.fetch as MockedFunction<typeof fetch>).mockResolvedValue(
      new Response(JSON.stringify({ isAuthenticated: false }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    )
    renderLoginPage()
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Join as Guest' })).toBeInTheDocument()
    })
    expect(mockNavigate).not.toHaveBeenCalledWith('/', expect.anything())
  })
})
