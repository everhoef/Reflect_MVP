import { Outlet } from 'react-router-dom'
import Header from '@/app/components/Header'
import { useRetroStateStore } from '@/modules/facilitation/store/retroStore'
import { retroPhaseToStageIndex } from '@/modules/facilitation/lib/retroPhaseToStageIndex'

export default function MainLayout() {
  const currentPhase = useRetroStateStore((s) => s.currentPhase)
  const currentStage = retroPhaseToStageIndex(currentPhase)

  return (
    <div className="flex flex-col min-h-screen bg-gray-50">
      <Header {...(currentStage ? { currentStage } : {})} />
      <main className="flex-grow container mx-auto px-4 py-6">
        <Outlet />
      </main>
    </div>
  )
}
