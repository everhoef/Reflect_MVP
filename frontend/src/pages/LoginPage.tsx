import { useState, useEffect } from 'react'
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

  useEffect(() => {
    void fetch('/api/me')
      .then((res) => {
        if (res.ok) return res.json() as Promise<{ isAuthenticated: boolean }>
        return null
      })
      .then((data) => {
        if (data?.isAuthenticated) {
          navigate('/', { replace: true })
        }
      })
      .catch(() => {})
  }, [navigate])

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
      <h1 className="text-3xl font-semibold text-gray-800">Welcome</h1>

      <div className="w-full max-w-sm space-y-4">
        <a
          href="/oauth2/authorization/github"
          className="w-full flex items-center justify-center gap-2 bg-gray-900 text-white rounded-lg px-4 py-2 text-sm font-medium hover:bg-gray-800 transition-colors"
        >
          <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20" aria-hidden="true">
            <path
              fillRule="evenodd"
              d="M10 0C4.477 0 0 4.484 0 10.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.531 1.032 1.531 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0110 4.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.203 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.942.359.31.678.921.678 1.856 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0020 10.017C20 4.484 15.522 0 10 0z"
              clipRule="evenodd"
            />
          </svg>
          Sign in with GitHub
        </a>
      </div>

      <div className="w-full max-w-sm flex items-center gap-3">
        <hr className="flex-1 border-gray-200" />
        <span className="text-xs text-gray-400 font-medium">or</span>
        <hr className="flex-1 border-gray-200" />
      </div>

      <div className="w-full max-w-sm space-y-1">
        <p className="text-sm font-medium text-gray-600 text-center">Join as Guest</p>
      </div>

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
