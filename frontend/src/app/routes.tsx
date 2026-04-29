import { Routes, Route } from 'react-router-dom'
import MainLayout from '@/app/layouts/MainLayout'
import HomePage from '@/modules/core/pages/HomePage'
import LoginPage from '@/modules/auth/pages/LoginPage'
import LobbyPage from '@/modules/facilitation/pages/LobbyPage'
import RetroPage from '@/modules/facilitation/pages/RetroPage'
import ManagerInboxPage from '@/modules/organization/pages/ManagerInboxPage'
import ProfilePage from '@/modules/auth/pages/ProfilePage'

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

