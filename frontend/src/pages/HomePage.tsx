import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { createRetrospective, joinRetrospective } from '@/hooks/api/useHome'
import { ApiError } from '@/lib/api-client'

export default function HomePage() {
  const navigate = useNavigate()
  const [sessionName, setSessionName] = useState('')
  const [retroId, setRetroId] = useState('')
  const [creating, setCreating] = useState(false)
  const [joining, setJoining] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!sessionName.trim()) return
    setCreating(true)
    setError(null)
    try {
      const data = await createRetrospective({ sessionName: sessionName.trim() })
      navigate(data.redirectUrl)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to create session')
    } finally {
      setCreating(false)
    }
  }

  const handleJoin = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!retroId.trim()) return
    setJoining(true)
    setError(null)
    try {
      const data = await joinRetrospective({ retroId: retroId.trim() })
      navigate(data.redirectUrl)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to join session')
    } finally {
      setJoining(false)
    }
  }

  return (
    <div className="flex flex-col items-center justify-center py-16 gap-8 px-4">
      <h1 className="text-3xl font-semibold text-gray-800">Welcome to Reflect.Direct</h1>

      {error && (
        <p className="text-red-600 text-sm">{error}</p>
      )}

      <div className="w-full max-w-sm space-y-6">
        <form onSubmit={(e) => void handleCreate(e)} className="space-y-3">
          <h2 className="text-lg font-medium text-gray-700">Create Retrospective</h2>
          <input
            name="sessionName"
            type="text"
            value={sessionName}
            onChange={(e) => setSessionName(e.target.value)}
            placeholder="Session name"
            className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-amber-400"
          />
          <button
            type="submit"
            disabled={creating || !sessionName.trim()}
            className="w-full bg-amber-500 text-white rounded-lg px-4 py-2 text-sm font-medium hover:bg-amber-600 disabled:opacity-40 transition-colors"
          >
            {creating ? 'Creating…' : 'Create Session'}
          </button>
        </form>

        <hr className="border-gray-200" />

        <form onSubmit={(e) => void handleJoin(e)} className="space-y-3">
          <h2 className="text-lg font-medium text-gray-700">Join Retrospective</h2>
          <input
            name="retroId"
            type="text"
            value={retroId}
            onChange={(e) => setRetroId(e.target.value)}
            placeholder="Session ID"
            className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-amber-400"
          />
          <button
            type="submit"
            disabled={joining || !retroId.trim()}
            className="w-full bg-gray-700 text-white rounded-lg px-4 py-2 text-sm font-medium hover:bg-gray-800 disabled:opacity-40 transition-colors"
          >
            {joining ? 'Joining…' : 'Join Session'}
          </button>
        </form>
      </div>
    </div>
  )
}
