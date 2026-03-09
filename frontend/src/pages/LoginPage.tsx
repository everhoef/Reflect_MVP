import { useState } from 'react'
import { useNavigate } from 'react-router-dom'

function getCsrfToken(): string | undefined {
  const raw = document.cookie
    .split('; ')
    .find((row) => row.startsWith('XSRF-TOKEN='))
    ?.split('=')[1]
  return raw ? decodeURIComponent(raw) : undefined
}

export default function LoginPage() {
  const navigate = useNavigate()
  const [displayName, setDisplayName] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleGuestLogin = async (e: React.FormEvent) => {
    e.preventDefault()
    const name = displayName.trim()
    if (!name) return
    setLoading(true)
    setError(null)
    try {
      const csrf = getCsrfToken()
      const formData = new URLSearchParams()
      formData.set('displayName', name)
      const res = await fetch('/auth/guest', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          ...(csrf ? { 'X-XSRF-TOKEN': csrf } : {}),
        },
        body: formData.toString(),
        redirect: 'manual',
      })
      if (res.ok || res.status === 302 || res.type === 'opaqueredirect') {
        navigate('/')
      } else {
        setError('Login failed. Please try again.')
      }
    } catch {
      setError('Login failed. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex flex-col items-center justify-center py-16 gap-8 px-4">
      <h1 className="text-3xl font-semibold text-gray-800">Join as Guest</h1>

      {error && (
        <p className="text-red-600 text-sm">{error}</p>
      )}

      <form onSubmit={(e) => void handleGuestLogin(e)} className="w-full max-w-sm space-y-4">
        <input
          name="displayName"
          type="text"
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
          placeholder="Your display name"
          className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-amber-400"
          autoFocus
        />
        <button
          type="submit"
          disabled={loading || !displayName.trim()}
          className="w-full bg-amber-500 text-white rounded-lg px-4 py-2 text-sm font-medium hover:bg-amber-600 disabled:opacity-40 transition-colors"
        >
          {loading ? 'Joining…' : 'Join as Guest'}
        </button>
      </form>
    </div>
  )
}
