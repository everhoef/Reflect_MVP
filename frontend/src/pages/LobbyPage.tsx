import { useParams } from 'react-router-dom'

export default function LobbyPage() {
  const { retroId } = useParams<{ retroId: string }>()

  return (
    <div className="flex flex-col items-center justify-center py-16">
      <h1 className="text-3xl font-semibold text-gray-800">Lobby</h1>
      <p className="mt-2 text-gray-500">Waiting room for retro: {retroId}</p>
    </div>
  )
}
