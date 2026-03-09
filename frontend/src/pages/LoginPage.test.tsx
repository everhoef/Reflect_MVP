import { render, screen } from '@testing-library/react'
import LoginPage from './LoginPage'

describe('LoginPage', () => {
  it('renders the Login heading', () => {
    render(<LoginPage />)
    expect(screen.getByRole('heading', { name: 'Login' })).toBeInTheDocument()
  })

  it('renders the sign in prompt text', () => {
    render(<LoginPage />)
    expect(screen.getByText('Sign in to continue')).toBeInTheDocument()
  })
})
