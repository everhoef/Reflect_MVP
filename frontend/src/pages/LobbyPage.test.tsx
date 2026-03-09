import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import LobbyPage from './LobbyPage'

function renderLobbyPage(retroId: string) {
  return render(
    <MemoryRouter initialEntries={[`/retro/${retroId}/lobby`]}>
      <Routes>
        <Route path="/retro/:retroId/lobby" element={<LobbyPage />} />
      </Routes>
    </MemoryRouter>
  )
}

describe('LobbyPage', () => {
  it('renders the Lobby heading', () => {
    renderLobbyPage('abc-123')
    expect(screen.getByRole('heading', { name: 'Lobby' })).toBeInTheDocument()
  })

  it('renders the retro ID from route params', () => {
    renderLobbyPage('abc-123')
    expect(screen.getByText(/abc-123/)).toBeInTheDocument()
  })

  it('renders waiting room description text', () => {
    renderLobbyPage('my-retro-id')
    expect(screen.getByText(/Waiting room for retro/i)).toBeInTheDocument()
  })

  it('renders the specific retroId in waiting room text', () => {
    renderLobbyPage('specific-retro-id')
    expect(screen.getByText(/specific-retro-id/)).toBeInTheDocument()
  })
})
