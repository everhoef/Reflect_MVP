import { render, screen } from '@testing-library/react'
import HomePage from './HomePage'

describe('HomePage', () => {
  it('renders the Home heading', () => {
    render(<HomePage />)
    expect(screen.getByRole('heading', { name: 'Home' })).toBeInTheDocument()
  })

  it('renders the welcome text', () => {
    render(<HomePage />)
    expect(screen.getByText('Welcome to Reflect.Direct')).toBeInTheDocument()
  })
})
