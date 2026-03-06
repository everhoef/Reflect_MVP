import { Routes, Route } from 'react-router-dom'
import MainLayout from './layouts/MainLayout'
import HomePage from './pages/HomePage'
import LoginPage from './pages/LoginPage'
import LobbyPage from './pages/LobbyPage'
import RetroPage from './pages/RetroPage'

export default function AppRoutes() {
  return (
    <Routes>
      <Route element={<MainLayout />}>
        <Route path="/" element={<HomePage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/retro/:retroId/lobby" element={<LobbyPage />} />
        <Route path="/retro/:retroId" element={<RetroPage />} />
      </Route>
    </Routes>
  )
}
