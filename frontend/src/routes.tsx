import { Routes, Route } from 'react-router-dom'
import MainLayout from './layouts/MainLayout'
import HomePage from './pages/HomePage'
import LoginPage from './pages/LoginPage'
import LobbyPage from './pages/LobbyPage'
import RetroPage from './pages/RetroPage'
import ManagerInboxPage from './pages/ManagerInboxPage'
import ProfilePage from './pages/ProfilePage'

export default function AppRoutes() {
  return (
    <Routes>
      <Route element={<MainLayout />}>
        <Route path="/" element={<HomePage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/manager/inbox" element={<ManagerInboxPage />} />
        <Route path="/retro/:retroId/lobby" element={<LobbyPage />} />
        <Route path="/retro/:retroId" element={<RetroPage />} />
        <Route path="/profile" element={<ProfilePage />} />
      </Route>
    </Routes>
  )
}

