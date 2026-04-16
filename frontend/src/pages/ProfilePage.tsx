import { useCurrentUser } from '@/hooks/api/useAuth'

export default function ProfilePage() {
  const { data: currentUser, isLoading, isError } = useCurrentUser()

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center py-16 px-4">
        <p className="text-gray-500">Loading profile...</p>
      </div>
    )
  }

  if (isError || !currentUser) {
    return (
      <div className="flex flex-col items-center justify-center py-16 px-4">
        <p className="text-red-500">Could not load profile. Please log in.</p>
      </div>
    )
  }

  return (
    <div className="flex flex-col items-center py-16 px-4">
      <div className="w-full max-w-md bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
        <div className="bg-amber-500 px-6 py-8 text-center">
          <div className="inline-flex items-center justify-center w-20 h-20 rounded-full bg-white text-amber-500 text-3xl font-bold shadow-sm mb-4">
            {currentUser.user?.displayName?.charAt(0).toUpperCase() || '?'}
          </div>
          <h1 className="text-2xl font-bold text-white">{currentUser.user?.displayName}</h1>
          <p className="text-amber-100 font-medium mt-1">
            {currentUser.isGuest ? 'Guest Participant' : 'Registered User'}
          </p>
        </div>
        
        <div className="px-6 py-6 space-y-4">
          <div>
            <h3 className="text-sm font-medium text-gray-500">Participant ID</h3>
            <p className="text-base text-gray-900 font-mono mt-1 bg-gray-50 p-2 rounded border border-gray-100 break-all">
              {currentUser.user?.id}
            </p>
          </div>
          
          <div>
            <h3 className="text-sm font-medium text-gray-500">Role</h3>
            <p className="text-base text-gray-900 mt-1">
              {currentUser.user?.role || 'User'}
            </p>
          </div>

          <div>
            <h3 className="text-sm font-medium text-gray-500">Authentication Type</h3>
            <p className="text-base text-gray-900 mt-1">
              {currentUser.authType}
            </p>
          </div>
        </div>
      </div>
    </div>
  )
}
