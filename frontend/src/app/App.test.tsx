import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import App from '@/app/App'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'

const queryClient = new QueryClient()

describe('App', () => {
  it('renders without crashing', () => {
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <App />
        </MemoryRouter>
      </QueryClientProvider>
    )
    expect(document.body).toBeTruthy()
  })

  it('renders home page at root route', () => {
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/']}>
          <App />
        </MemoryRouter>
      </QueryClientProvider>
    )
    expect(screen.getByText('Welcome to Reflect.Direct')).toBeInTheDocument()
  })
})
