import { useState, useRef, useEffect } from 'react'
import { Link } from 'react-router-dom'
import { User, LogOut } from 'lucide-react'
import { useCurrentUser } from '@/hooks/api/useAuth'
import { csrfHeaders } from '@/lib/api-client'

export default function UserMenu() {
  const { data: currentUser, isLoading } = useCurrentUser()
  const [isOpen, setIsOpen] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setIsOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const handleLogout = async () => {
    try {
      const response = await fetch('/logout', {
        method: 'POST',
        headers: csrfHeaders(),
      })
      if (response.ok) {
        window.location.href = '/login'
      }
    } catch (error) {
      console.error('Logout failed:', error)
    }
  }

  if (isLoading || !currentUser || !currentUser.isAuthenticated) {
    return <div className="w-24" />
  }

  const initials = currentUser.user?.displayName?.charAt(0).toUpperCase() || 'U'

  return (
    <div className="relative w-24 flex justify-end" ref={menuRef}>
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="flex items-center justify-center w-9 h-9 rounded-full bg-amber-100 text-amber-700 font-medium hover:bg-amber-200 transition-colors focus:outline-none focus:ring-2 focus:ring-amber-500 focus:ring-offset-2"
        aria-expanded={isOpen}
        aria-haspopup="true"
        aria-label="User menu"
      >
        {initials}
      </button>

      {isOpen && (
        <div className="absolute right-0 top-full mt-2 w-56 bg-white rounded-lg shadow-lg py-1 border border-gray-100 z-50">
          <div className="px-4 py-3 border-b border-gray-100">
            <p className="text-sm font-medium text-gray-900 truncate">
              {currentUser.user?.displayName}
            </p>
            <p className="text-xs text-gray-500 truncate mt-0.5">
              {currentUser.isGuest ? 'Guest' : currentUser.user?.role}
            </p>
          </div>
          
          <div className="py-1">
            <Link
              to="/profile"
              className="flex items-center gap-2 px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 hover:text-amber-600 transition-colors"
              onClick={() => setIsOpen(false)}
            >
              <User className="w-4 h-4" />
              My Profile
            </Link>
            
            <button
              onClick={handleLogout}
              className="flex items-center gap-2 w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 hover:text-red-600 transition-colors"
            >
              <LogOut className="w-4 h-4" />
              Log out
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
