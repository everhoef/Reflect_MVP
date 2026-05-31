import { Navigate } from "react-router-dom"
import { useCurrentUser } from '@/modules/auth/hooks/useAuth'
import { useManagerEscalations } from '@/modules/organization/hooks/useManagerEscalations'

export default function ManagerInboxPage() {
  const { data: user, isLoading: userLoading } = useCurrentUser()
  const isManager = user?.user?.role === "MANAGER"
  
  const { data: escalations, isLoading: escalationsLoading, error } = useManagerEscalations(!!user && isManager)

  if (userLoading) {
    return <div className="p-8">Loading access...</div>
  }

  if (!user || !isManager) {
    return <Navigate to="/" replace />
  }

  if (escalationsLoading) {
    return <div className="p-8">Loading escalations...</div>
  }

  if (error) {
    return (
      <div className="p-8 text-red-500">
        Error loading escalations. Make sure you are authenticated.
      </div>
    )
  }

  return (
    <div className="container mx-auto p-8 max-w-4xl">
      <h1 className="text-3xl font-bold mb-8">Manager Escalation Inbox</h1>
      
      {!escalations || escalations.length === 0 ? (
        <div className="bg-slate-50 border border-slate-200 rounded-lg p-12 text-center text-slate-500">
          <p className="text-lg mb-2">No items escalated to management.</p>
          <p className="text-sm">When teams escalate issues during retrospectives, they will appear here.</p>
        </div>
      ) : (
        <div className="space-y-4">
          {escalations.map((item) => (
            <div key={item.id} className="bg-white border border-slate-200 rounded-lg p-6 shadow-sm">
              <div className="flex justify-between items-start mb-4">
                <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">
                  Escalated on {item.createdAt ? new Date(item.createdAt).toLocaleDateString() : 'Unknown date'}
                </span>
                <span className={`px-2 py-1 rounded-full text-xs font-medium ${item.thresholdMet ? 'bg-red-100 text-red-800' : 'bg-amber-100 text-amber-800'}`}>
                  {item.voteCount ?? 0} / {item.threshold ?? 0} votes
                </span>
              </div>
              <p className="text-slate-800 text-lg whitespace-pre-wrap">{item.problemDescription}</p>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
