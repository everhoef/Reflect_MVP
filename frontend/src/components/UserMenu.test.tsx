import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import UserMenu from './UserMenu'
import { useCurrentUser } from '@/hooks/api/useAuth'
import type { UseQueryResult } from '@tanstack/react-query'

vi.mock('@/hooks/api/useAuth', () => ({
  useCurrentUser: vi.fn(),
}))

function mockQuery<T>(data: T, isLoading = false): UseQueryResult<T, Error> {
  return {
    data,
    isLoading,
  } as unknown as UseQueryResult<T, Error>
}

describe('UserMenu', () => {
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
})
