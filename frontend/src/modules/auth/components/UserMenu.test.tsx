import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import UserMenu from '@/modules/auth/components/UserMenu'
import { useCurrentUser } from '@/modules/auth/hooks/useAuth'
import type { UseQueryResult } from '@tanstack/react-query'

vi.mock('@/modules/auth/hooks/useAuth', () => ({
  useCurrentUser: vi.fn(),
}))

// Mock fetch globally
const mockFetch = vi.fn()
// @ts-expect-error global is not defined in all environments
global.fetch = mockFetch

// Mock window.location
const originalLocation = window.location
const mockLocation = { ...originalLocation, href: '' }
Object.defineProperty(window, 'location', {
  value: mockLocation,
  writable: true,
})

function mockQuery<T>(data: T, isLoading = false): UseQueryResult<T, Error> {
  return {
    data,
    isLoading,
  } as unknown as UseQueryResult<T, Error>
}

describe('UserMenu', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockLocation.href = ''
  })

  it('renders null when not authenticated', () => {
    vi.mocked(useCurrentUser).mockReturnValue(mockQuery(null))

    const { container } = render(
      <BrowserRouter>
        <UserMenu />
      </BrowserRouter>
    )
    expect(container.firstChild).toHaveClass('w-24')
  })

  it('renders user initials when authenticated', () => {
    vi.mocked(useCurrentUser).mockReturnValue(mockQuery({
      isAuthenticated: true,
      user: { id: '1', displayName: 'John Doe', role: 'USER' },
      isGuest: false,
      authType: 'OIDC',
    }))

    render(
      <BrowserRouter>
        <UserMenu />
      </BrowserRouter>
    )
    
    expect(screen.getByRole('button', { name: /user menu/i })).toHaveTextContent('J')
  })

  it('opens menu and shows options when clicked', () => {
    vi.mocked(useCurrentUser).mockReturnValue(mockQuery({
      isAuthenticated: true,
      user: { id: '1', displayName: 'John Doe', role: 'USER' },
      isGuest: false,
      authType: 'OIDC',
    }))

    render(
      <BrowserRouter>
        <UserMenu />
      </BrowserRouter>
    )
    
    const button = screen.getByRole('button', { name: /user menu/i })
    fireEvent.click(button)
    
    expect(screen.getByText('John Doe')).toBeInTheDocument()
    expect(screen.getByText('My Profile')).toBeInTheDocument()
    expect(screen.getByText('Log out')).toBeInTheDocument()
  })

  it('redirects to /login on successful logout', async () => {
    vi.mocked(useCurrentUser).mockReturnValue(mockQuery({
      isAuthenticated: true,
      user: { id: '1', displayName: 'John Doe', role: 'USER' },
      isGuest: false,
      authType: 'OIDC',
    }))

    mockFetch.mockResolvedValueOnce({ ok: true })

    render(
      <BrowserRouter>
        <UserMenu />
      </BrowserRouter>
    )
    
    fireEvent.click(screen.getByRole('button', { name: /user menu/i }))
    fireEvent.click(screen.getByText('Log out'))
    
    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledWith('/logout', expect.any(Object))
      expect(mockLocation.href).toBe('/login')
    })
  })

  it('does not redirect on failed logout (e.g., 403)', async () => {
    vi.mocked(useCurrentUser).mockReturnValue(mockQuery({
      isAuthenticated: true,
      user: { id: '1', displayName: 'John Doe', role: 'USER' },
      isGuest: false,
      authType: 'OIDC',
    }))

    mockFetch.mockResolvedValueOnce({ ok: false, status: 403 })
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})

    render(
      <BrowserRouter>
        <UserMenu />
      </BrowserRouter>
    )
    
    fireEvent.click(screen.getByRole('button', { name: /user menu/i }))
    fireEvent.click(screen.getByText('Log out'))
    
    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledWith('/logout', expect.any(Object))
      expect(mockLocation.href).not.toBe('/login')
      expect(mockLocation.href).toBe('')
    })
    consoleSpy.mockRestore()
  })
})
