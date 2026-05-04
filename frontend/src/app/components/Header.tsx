import { Link, useLocation } from 'react-router-dom'
import UserMenu from '@/modules/auth/components/UserMenu'

type StageStatus = 'complete' | 'in-progress' | 'to-do'

interface Stage {
  id: number
  label: string
  shortLabel: string
}

const STAGES: Stage[] = [
  { id: 1, label: 'Set the Stage', shortLabel: 'Set the Stage' },
  { id: 2, label: 'Gather Data', shortLabel: 'Gather Data' },
  { id: 3, label: 'Generate Insights', shortLabel: 'Insights' },
  { id: 4, label: 'Decide Actions', shortLabel: 'Actions' },
  { id: 5, label: 'Close Retro', shortLabel: 'Close' },
]

interface HeaderProps {
  currentStage?: number
}

function getStageStatus(stageId: number, currentStage?: number): StageStatus {
  if (currentStage === undefined) return 'to-do'
  if (stageId < currentStage) return 'complete'
  if (stageId === currentStage) return 'in-progress'
  return 'to-do'
}

export default function Header({ currentStage }: HeaderProps) {
  const location = useLocation()
  const isRetroPage =
    location.pathname.includes('/retro/') && !location.pathname.endsWith('/lobby')

  return (
    <header className="bg-white shadow-sm border-b border-amber-100 relative z-40">
      <div className="container mx-auto px-4">
        <div className="flex items-center justify-between h-14 relative">
          <Link
            to="/"
            className="text-xl font-semibold tracking-tight text-[#C49A1A]"
          >
            Reflect.Direct
          </Link>

          {isRetroPage && (
            <nav aria-label="Retrospective stages" data-testid="stage-progress-bar" className="flex items-center gap-1">
              {STAGES.map((stage, index) => {
                const status = getStageStatus(stage.id, currentStage)
                return (
                  <div key={stage.id} className="flex items-center">
                    {index > 0 && (() => {
                      const prevStatus = getStageStatus(stage.id - 1, currentStage)
                      const connectorStatus =
                        status === 'complete'
                          ? 'complete'
                          : prevStatus === 'in-progress'
                          ? 'in-progress'
                          : 'to-do'
                      return (
                        <div
                          data-connector-index={index}
                          data-connector-status={connectorStatus}
                          className={`w-6 h-px mx-1 ${
                            connectorStatus === 'complete' || connectorStatus === 'in-progress'
                              ? 'bg-amber-400'
                              : 'bg-gray-200'
                          }`}
                        />
                      )
                    })()}

                    <div
                      data-stage-index={stage.id}
                      data-stage-status={status}
                      className={`flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium transition-all ${
                        status === 'complete'
                          ? 'bg-amber-100 text-amber-700'
                          : status === 'in-progress'
                          ? 'bg-amber-500 text-white shadow-sm'
                          : 'bg-gray-100 text-gray-400'
                      }`}
                      aria-current={status === 'in-progress' ? 'step' : undefined}
                    >
                      {status === 'complete' && (
                        <svg
                          className="w-3 h-3"
                          fill="none"
                          viewBox="0 0 24 24"
                          stroke="currentColor"
                          strokeWidth={2.5}
                        >
                          <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                        </svg>
                      )}
                      {status === 'in-progress' && (
                        <div className="w-1.5 h-1.5 rounded-full bg-white opacity-80" />
                      )}
                      <span className="hidden sm:inline">{stage.label}</span>
                      <span className="sm:hidden">{stage.shortLabel}</span>
                    </div>
                  </div>
                )
              })}
            </nav>
          )}

          <UserMenu />
        </div>
      </div>
    </header>
  )
}
